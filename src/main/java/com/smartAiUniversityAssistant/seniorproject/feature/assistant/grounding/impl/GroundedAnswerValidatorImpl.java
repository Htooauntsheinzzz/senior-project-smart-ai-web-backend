package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingValidationResult.Outcome;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Extracts the claims that can be checked reliably from the generated answer and compares each with the facts of the
 * current request's evidence (verified tool data, the backend's ambiguity candidates and the identifiers the backend
 * queried). Course codes, section numbers, rooms, class times, weekdays and years must appear in the evidence; credit
 * hours, seat counts, semester numbers, program names ("your program is ...") and lecturer names must equal an evidence
 * value of the same kind ("Professor John Smith" matches
 * "Dr. John Smith", "three credit hours" matches 3). A claim of a kind the evidence has no value for is unsupported;
 * a different value of a kind the evidence does have is inconsistent. Wording, negations and free-form explanations
 * are not judged: this is not a semantic hallucination detector, only a deterministic check of known value types.
 * Redis conversation history is never evidence, so a room remembered from an earlier answer fails if PostgreSQL now
 * returns another one.
 */
@Component
public class GroundedAnswerValidatorImpl implements GroundedAnswerValidator {
    // Letter-digit boundaries are ASCII so a code written right after Thai or Myanmar text is still found.
    private static final Pattern COURSE_CODE = Pattern.compile("(?<![A-Za-z0-9])([A-Z]{2,5})-?(\\d{3,4})([A-Z]?)(?![A-Za-z0-9])");
    private static final Pattern SECTION = Pattern.compile(
            "(?i)(?<![a-z0-9])sec(?:tion)?\\.?\\s*(?:no\\.?|number|#)?\\s*-?\\s*(\\d{1,6})(?![0-9])");
    private static final String NUMBER_WORD = "\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve";
    private static final Pattern CREDITS = Pattern.compile("(?i)(?<![\\w.])(" + NUMBER_WORD + ")\\s*-?\\s*(?:credit|หน่วยกิต|ခရက်ဒစ်)");
    private static final Pattern CREDITS_AFTER = Pattern.compile("(?i)(?<![a-z])credit(?:s|\\s*-?\\s*hours?)?\\s*(?:is|are|of|:|=)?\\s*(\\d{1,2})(?![\\d:-]|\\.\\d)");
    private static final Pattern SEATS_OF = Pattern.compile("(?i)(?<![\\w.:/-])(\\d{1,4})\\s*(?:/|\\s(?:of|out of)\\s)\\s*(\\d{1,4})\\s+(?:[a-z]+\\s+)?seats?(?![a-z])");
    private static final Pattern SEATS_BEFORE = Pattern.compile("(?i)(?<![\\w.:/-])(\\d{1,4})\\s+(?:[a-z]+\\s+){0,2}seats?(?![a-z])");
    private static final Pattern SEATS_AFTER = Pattern.compile(
            "(?i)(?<![a-z])(?:seats?\\s*(?:are\\s+|is\\s+)?(?:available|left|remaining|taken|occupied|filled|free|open)?|capacity\\s*(?:is|of)?)\\s*[:=]?\\s*(\\d{1,4})(?![\\d:-]|\\.\\d)");
    private static final Pattern ROOM_NUMBER = Pattern.compile("(?<![\\w:./-])(\\d{1,3}-\\d{2,4}[A-Za-z]?)(?![\\w:/-])");
    private static final Pattern ROOM_WORD = Pattern.compile("(?i)(?:(?<![a-z])room|(?<![a-z])rm\\.?|ห้อง)\\s*:?\\s*(?:no\\.?\\s*|number\\s*)?([A-Za-z]?\\d[A-Za-z0-9-]*)");
    // Up to three capitalized words, stopping before capitalized function words ("Dr. John Smith On Tuesday").
    private static final String NAME = "([A-Z][\\p{L}'’-]+(?:\\s+(?!(?:On|In|At|For|And|Or|The|Is|Was|From|To|Of|With|"
            + "Teaches|Every|Room|Section|Schedule)(?![\\p{L}]))[A-Z][\\p{L}'’-]+){0,2})";
    private static final String TITLE = "(?:Dr|Prof|Professor|Ajarn|Acharn|Aj)\\.?\\s+";
    private static final Pattern TITLED_NAME = Pattern.compile("(?<![A-Za-z])" + TITLE + NAME);
    private static final Pattern TAUGHT_BY = Pattern.compile(
            "(?i:taught by|lecturer is|instructor is|teacher is|lecturer:|instructor:)\\s+(?:" + TITLE + ")?" + NAME);
    private static final Pattern PROGRAM = Pattern.compile("(?i:program|programme|major)\\s+(?i:is|:|=)\\s+(?:the\\s+)?"
            + "([A-Z][\\p{L}&-]*(?:\\s+(?:of|and|in|&)\\s+[A-Z][\\p{L}&-]*|\\s+[A-Z][\\p{L}&-]*){0,5})");
    private static final Set<String> NOT_A_NAME = Set.of("the", "a", "an", "your", "this", "that", "no", "not", "unknown",
            "tba", "tbd", "none", "currently", "unavailable");
    private static final Pattern SEMESTER = Pattern.compile("(?i)(?<![a-z])(?:semester|term|ภาคการศึกษาที่|ภาคเรียนที่|เทอม)\\s*(\\d{1,2})(?!\\d)");
    private static final Pattern YEAR = Pattern.compile("(?<![\\d.:/-])((?:19|20)\\d{2})(?![\\d:/-])");
    private static final Pattern TIME = Pattern.compile("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)");
    private static final Pattern WEEKDAY = Pattern.compile(
            "(?i)(?<![a-z])(monday|tuesday|wednesday|thursday|friday|saturday|sunday)s?(?![a-z])"
            + "|วัน(จันทร์|อังคาร|พุธ|พฤหัสบดี|พฤหัส|ศุกร์|เสาร์|อาทิตย์)");
    private static final Map<String, String> THAI_DAYS = Map.of("จันทร์", "MONDAY", "อังคาร", "TUESDAY", "พุธ", "WEDNESDAY",
            "พฤหัสบดี", "THURSDAY", "พฤหัส", "THURSDAY", "ศุกร์", "FRIDAY", "เสาร์", "SATURDAY", "อาทิตย์", "SUNDAY");
    private static final Map<String, Integer> WORDS = Map.ofEntries(Map.entry("one", 1), Map.entry("two", 2),
            Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7),
            Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11), Map.entry("twelve", 12));
    private static final Set<String> SEAT_FIELDS = Set.of("capacity", "occupiedSeats", "availableSeats", "maximumStudentsPerSection");
    private static final Set<String> LECTURER_FIELDS = Set.of("lecturerNameEn", "lecturerNameTh", "lecturerNickname");

    @Override
    public boolean containsUniversityFacts(String answer) {
        if (answer == null || answer.isBlank()) return false;
        return !courseCodes(answer).isEmpty() || !rooms(answer).isEmpty() || !numbers(SECTION, answer).isEmpty()
                || !lecturerNames(answer).isEmpty() || !programs(answer).isEmpty();
    }

    @Override
    public GroundingValidationResult validate(String generatedAnswer, List<AcademicEvidence> evidence) {
        if (evidence == null || evidence.isEmpty())
            return new GroundingValidationResult(Outcome.MISSING_REQUIRED_EVIDENCE, List.of());
        if (generatedAnswer == null || generatedAnswer.isBlank()) return GroundingValidationResult.grounded();
        Facts facts = Facts.of(evidence);
        var unsupported = new LinkedHashSet<String>();
        var inconsistent = new LinkedHashSet<String>();

        for (String code : courseCodes(generatedAnswer)) if (!facts.courseCodes.contains(code)) unsupported.add("COURSE_CODE");
        for (long section : numbers(SECTION, generatedAnswer)) if (!facts.sections.contains(section)) unsupported.add("SECTION");
        check(credits(generatedAnswer), facts.credits, "CREDIT_HOURS", unsupported, inconsistent);
        check(seats(generatedAnswer), facts.seats, "SEATS", unsupported, inconsistent);
        check(rooms(generatedAnswer), facts.rooms, "ROOM", unsupported, inconsistent);
        for (String program : programs(generatedAnswer)) {
            if (facts.programs.isEmpty()) unsupported.add("PROGRAM");
            else if (facts.programs.stream().noneMatch(known -> known.contains(program) || program.contains(known))) inconsistent.add("PROGRAM");
        }
        for (List<String> name : lecturerNames(generatedAnswer)) {
            if (facts.lecturers.isEmpty()) unsupported.add("LECTURER");
            else if (facts.lecturers.stream().noneMatch(known -> known.containsAll(name))) inconsistent.add("LECTURER");
        }
        check(numbers(SEMESTER, generatedAnswer), facts.semesters, "SEMESTER", unsupported, inconsistent);
        for (long year : numbers(YEAR, generatedAnswer)) if (!facts.years.contains(year)) unsupported.add("YEAR");
        for (int time : times(generatedAnswer)) if (!facts.times.contains(time)) unsupported.add("TIME");
        for (String day : weekdays(generatedAnswer)) if (!facts.weekdays.contains(day)) unsupported.add("WEEKDAY");

        if (unsupported.isEmpty() && inconsistent.isEmpty()) return GroundingValidationResult.grounded();
        var issues = new ArrayList<>(inconsistent);
        unsupported.stream().filter(i -> !issues.contains(i)).forEach(issues::add);
        Outcome outcome = !facts.anyVerified ? Outcome.UNVERIFIABLE_ANSWER
                : !inconsistent.isEmpty() ? Outcome.INCONSISTENT_ACADEMIC_VALUE : Outcome.UNSUPPORTED_ACADEMIC_CLAIM;
        return new GroundingValidationResult(outcome, issues);
    }

    // No evidence value of this kind: unsupported. Evidence of this kind with other values: inconsistent.
    private static <T> void check(Collection<T> claims, Set<T> known, String kind, Set<String> unsupported, Set<String> inconsistent) {
        for (T claim : claims) {
            if (known.contains(claim)) continue;
            (known.isEmpty() ? unsupported : inconsistent).add(kind);
        }
    }

    static Set<String> courseCodes(String text) {
        var out = new LinkedHashSet<String>();
        Matcher m = COURSE_CODE.matcher(text);
        while (m.find()) out.add(m.group(1) + m.group(2) + m.group(3));
        return out;
    }

    private static Set<Long> numbers(Pattern pattern, String text) {
        var out = new LinkedHashSet<Long>();
        Matcher m = pattern.matcher(text);
        while (m.find()) out.add(Long.parseLong(m.group(1)));
        return out;
    }

    private static Set<Long> credits(String text) {
        var out = new LinkedHashSet<Long>();
        for (Pattern p : List.of(CREDITS, CREDITS_AFTER)) {
            Matcher m = p.matcher(text);
            while (m.find()) out.add(number(m.group(1)));
        }
        return out;
    }

    private static Set<Long> seats(String text) {
        var out = new LinkedHashSet<Long>();
        Matcher of = SEATS_OF.matcher(text);
        while (of.find()) {
            out.add(Long.parseLong(of.group(1)));
            out.add(Long.parseLong(of.group(2)));
        }
        out.addAll(numbers(SEATS_BEFORE, text));
        out.addAll(numbers(SEATS_AFTER, text));
        return out;
    }

    static Set<String> rooms(String text) {
        var out = new LinkedHashSet<String>();
        for (Pattern p : List.of(ROOM_NUMBER, ROOM_WORD)) {
            Matcher m = p.matcher(text);
            while (m.find()) out.add(room(m.group(1)));
        }
        return out;
    }

    private static List<List<String>> lecturerNames(String text) {
        var out = new ArrayList<List<String>>();
        for (Pattern p : List.of(TITLED_NAME, TAUGHT_BY)) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                List<String> tokens = nameTokens(m.group(1));
                if (!tokens.isEmpty() && !NOT_A_NAME.contains(tokens.getFirst()) && !out.contains(tokens)) out.add(tokens);
            }
        }
        return out;
    }

    private static Set<String> programs(String text) {
        var out = new LinkedHashSet<String>();
        Matcher m = PROGRAM.matcher(text);
        while (m.find()) out.add(m.group(1).toLowerCase(Locale.ROOT).strip());
        return out;
    }

    private static Set<Integer> times(String text) {
        var out = new LinkedHashSet<Integer>();
        Matcher m = TIME.matcher(text);
        while (m.find()) out.add(Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)));
        return out;
    }

    private static Set<String> weekdays(String text) {
        var out = new LinkedHashSet<String>();
        Matcher m = WEEKDAY.matcher(text);
        while (m.find()) out.add(m.group(1) != null ? m.group(1).toUpperCase(Locale.ROOT) : THAI_DAYS.get(m.group(2)));
        return out;
    }

    private static long number(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return WORDS.containsKey(lower) ? WORDS.get(lower) : Long.parseLong(lower);
    }

    private static String room(String value) {
        return value.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    // Lower-case name words without titles, punctuation or a possessive "'s".
    private static List<String> nameTokens(String name) {
        var tokens = new ArrayList<String>();
        for (String raw : name.toLowerCase(Locale.ROOT).split("\\s+")) {
            String token = raw.replaceAll("['’]s$", "").replaceAll("[^\\p{L}\\p{N}-]", "");
            if (!token.isEmpty() && !Set.of("dr", "prof", "professor", "ajarn", "acharn", "aj").contains(token)) tokens.add(token);
        }
        return tokens;
    }

    /** The checkable facts of one request's evidence. */
    private static final class Facts {
        final Set<String> courseCodes = new HashSet<>();
        final Set<Long> sections = new HashSet<>();
        final Set<Long> credits = new HashSet<>();
        final Set<Long> seats = new HashSet<>();
        final Set<String> rooms = new HashSet<>();
        final List<List<String>> lecturers = new ArrayList<>();
        final Set<String> programs = new HashSet<>();
        final Set<Long> semesters = new HashSet<>();
        final Set<Long> years = new HashSet<>();
        final Set<Integer> times = new HashSet<>();
        final Set<String> weekdays = new HashSet<>();
        boolean anyVerified;

        static Facts of(List<AcademicEvidence> evidence) {
            var facts = new Facts();
            for (AcademicEvidence e : evidence) {
                if (e.requestedIdentifier() != null) {
                    facts.courseCodes.addAll(courseCodes(e.requestedIdentifier().toUpperCase(Locale.ROOT)));
                    if (e.requestedIdentifier().matches("\\d{1,18}")) facts.sections.add(Long.parseLong(e.requestedIdentifier()));
                }
                boolean trusted = e.verified() || e.status() == AcademicEvidenceStatus.AMBIGUOUS;
                if (!trusted) continue;
                facts.anyVerified |= e.status() == AcademicEvidenceStatus.VERIFIED;
                facts.collect(null, e.verifiedData());
            }
            return facts;
        }

        private void collect(String key, Object value) {
            if (value instanceof Map<?, ?> map) {
                map.forEach((k, v) -> collect(String.valueOf(k), v));
                return;
            }
            if (value instanceof Collection<?> list) {
                list.forEach(v -> collect(key, v));
                return;
            }
            if (value == null) return;
            String text = String.valueOf(value);
            if ("creditHours".equals(key) && value instanceof Number n) credits.add(n.longValue());
            if (key != null && SEAT_FIELDS.contains(key) && value instanceof Number n) seats.add(n.longValue());
            // "room" is kept for older evidence; published meetings carry roomNumber (spec 40).
            if ("room".equals(key) || "roomNumber".equals(key)) rooms.addAll(roomsOf(text));
            if ("courseSectionId".equals(key) && value instanceof Number n) sections.add(n.longValue());
            if ("sectionNumber".equals(key)) {
                Matcher digits = Pattern.compile("(\\d{1,6})").matcher(text);
                if (digits.find()) sections.add(Long.parseLong(digits.group(1)));
            }
            if ("programName".equals(key) && !text.isBlank()) programs.add(text.toLowerCase(Locale.ROOT).strip());
            if (key != null && LECTURER_FIELDS.contains(key)) {
                List<String> tokens = nameTokens(text);
                if (!tokens.isEmpty()) lecturers.add(tokens);
            }
            if (key != null && key.startsWith("semesterName")) semesters.addAll(numbers(SEMESTER, text));
            years.addAll(numbers(YEAR, text));
            courseCodes.addAll(courseCodes(text.toUpperCase(Locale.ROOT)));
            times.addAll(GroundedAnswerValidatorImpl.times(text));
            weekdays.addAll(GroundedAnswerValidatorImpl.weekdays(text));
        }

        private static Set<String> roomsOf(String text) {
            var out = new HashSet<String>(rooms(text));
            out.add(room(text.strip()));
            return out;
        }
    }
}
