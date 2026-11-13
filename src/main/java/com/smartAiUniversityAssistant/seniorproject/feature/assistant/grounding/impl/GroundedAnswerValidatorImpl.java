package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingValidationResult.Outcome;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolNames;
import java.math.BigDecimal;
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
 * <p>
 * Step 15A: a passage of search_university_knowledge that was really retrieved (VERIFIED) supports the values written in
 * it: its whole numbers and number words as credits, seats, sections and semesters, its rooms and pages, its names, its
 * program names, the years of its academic year and its grade point averages. Only while such document evidence is
 * present, credit totals with three digits (CREDITS_WIDE) and grade point averages (GPA) are claims too. Without it the
 * check is exactly the Step 11 check. Amounts of money, percentages and numbers of days are still not checked.
 * <p>
 * Step 16A: a VERIFIED result of get_my_academic_progress adds its earned and current credits and its grade point
 * average as facts and also switches on the wide credit and GPA claims. While it is present, a claim about the
 * Student's standing (STANDING: "you are eligible", "you have met all …", "you will graduate") is always unsupported,
 * because no evidence can support it. STANDING is a phrase list: paraphrases and Myanmar wordings are not caught.
 * <p>
 * Step 16B: "114 more credits" is a credit claim too (CREDITS_MORE), and with VERIFIED progress and VERIFIED passages in the
 * same request three derived credit values are facts: earned + current, |p − earned| and |p − (earned + current)|, where p
 * is a whole number of a passage that the answer also states. Whether the compared requirement is the right one is not
 * checked.
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
    // Step 15A: the same credit patterns with three digits, used instead of CREDITS/CREDITS_AFTER with document evidence.
    private static final Pattern CREDITS_WIDE = Pattern.compile("(?i)(?<![\\w.])(\\d{1,3}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\s*-?\\s*(?:credit|หน่วยกิต|ခရက်ဒစ်)");
    private static final Pattern CREDITS_AFTER_WIDE = Pattern.compile("(?i)(?<![a-z])credit(?:s|\\s*-?\\s*hours?)?\\s*(?:is|are|of|:|=)?\\s*(\\d{1,3})(?![\\d:-]|\\.\\d)");
    // Step 16B: a number, then more/additional/remaining/further/outstanding, then credits.
    private static final Pattern CREDITS_MORE = Pattern.compile("(?i)(?<![\\w.])(\\d{1,3})\\s+(?:more|additional|remaining|further|outstanding)"
            + "\\s+(?:credit|หน่วยกิต|ခရက်ဒစ်)");
    private static final Pattern GPA_AFTER = Pattern.compile("(?i)(?<![a-z])(?:c?gpax?|grade[\\s-]point\\s+average|เกรดเฉลี่ย(?:สะสม)?|ผลการเรียนเฉลี่ย(?:สะสม)?)[^\\d\\n]{0,40}?([0-4]\\.\\d{1,2})(?!\\d)");
    private static final Pattern GPA_BEFORE = Pattern.compile("(?i)(?<![\\d.])([0-4]\\.\\d{1,2})\\s*(?:c?gpax?|grade[\\s-]point\\s+average)(?![a-z])");
    // Facts of a passage: whole numbers (not the digits of a decimal number), number and ordinal words, decimal GPAs.
    private static final Pattern WHOLE_NUMBER = Pattern.compile("(?<![\\d.])(\\d{1,6})(?!\\d|\\.\\d)");
    private static final Pattern NUMBER_WORDS = Pattern.compile("(?i)(?<![a-z])(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|"
            + "first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth)(?![a-z])");
    private static final Pattern GPA_VALUE = Pattern.compile("(?<![\\d.])([0-4]\\.\\d{1,2})(?!\\d)");
    private static final Pattern ACADEMIC_YEAR = Pattern.compile("^\\s*(\\d{4})-(\\d{4})\\s*$");
    private static final Map<String, Integer> ORDINALS = Map.ofEntries(Map.entry("first", 1), Map.entry("second", 2),
            Map.entry("third", 3), Map.entry("fourth", 4), Map.entry("fifth", 5), Map.entry("sixth", 6), Map.entry("seventh", 7),
            Map.entry("eighth", 8), Map.entry("ninth", 9), Map.entry("tenth", 10), Map.entry("eleventh", 11), Map.entry("twelfth", 12));
    // Step 16A: claims about the Student's standing. Not after "before/until/whether/if/once/when/that".
    private static final Pattern STANDING_EN = Pattern.compile("(?i)(?<!(?:before|until|whether|if|once|when|that)\\s)(?<![a-z])you"
            + "(?:'re|’re|\\s+are|\\s+have|'ve|’ve)?\\s+(?:now\\s+|already\\s+)?(?:eligible|qualified|(?:met|meet|satisfied|fulfilled|completed)"
            + "\\s+(?:all|every)(?![a-z])|on\\s+track\\s+to\\s+graduate)");
    private static final Pattern STANDING_GRAD = Pattern.compile(
            "(?i)(?<!(?:before|until|whether|if|once|when|that)\\s)(?<![a-z])you\\s+(?:will|can|are\\s+able\\s+to)\\s+graduate(?![a-z])");
    private static final List<String> STANDING_TH = List.of("คุณมีสิทธิ์สำเร็จการศึกษา", "คุณมีสิทธิ์จบการศึกษา", "คุณจะจบการศึกษา",
            "คุณจบการศึกษาได้", "คุณผ่านเกณฑ์ครบ", "ครบทุกเงื่อนไขแล้ว");
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
        facts.derive(generatedAnswer);
        var unsupported = new LinkedHashSet<String>();
        var inconsistent = new LinkedHashSet<String>();

        for (String code : courseCodes(generatedAnswer)) if (!facts.courseCodes.contains(code)) unsupported.add("COURSE_CODE");
        for (long section : numbers(SECTION, generatedAnswer)) if (!facts.sections.contains(section)) unsupported.add("SECTION");
        check(credits(generatedAnswer, facts.wide()), facts.credits, "CREDIT_HOURS", unsupported, inconsistent);
        if (facts.wide()) check(gpas(generatedAnswer), facts.gpas, "GPA", unsupported, inconsistent);
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
        if (facts.progressEvidence && claimsStanding(generatedAnswer)) unsupported.add("STANDING");

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

    private static Set<Long> credits(String text, boolean wide) {
        var out = new LinkedHashSet<Long>();
        for (Pattern p : wide ? List.of(CREDITS_WIDE, CREDITS_AFTER_WIDE, CREDITS_MORE) : List.of(CREDITS, CREDITS_AFTER)) {
            Matcher m = p.matcher(text);
            while (m.find()) out.add(number(m.group(1)));
        }
        return out;
    }

    /** Step 16A: whether the text claims the Student's standing (eligible, all requirements met, will graduate). */
    static boolean claimsStanding(String text) {
        return STANDING_EN.matcher(text).find() || STANDING_GRAD.matcher(text).find() || STANDING_TH.stream().anyMatch(text::contains);
    }

    static Set<BigDecimal> gpas(String text) {
        var out = new LinkedHashSet<BigDecimal>();
        for (Pattern p : List.of(GPA_AFTER, GPA_BEFORE)) {
            Matcher m = p.matcher(text);
            while (m.find()) out.add(new BigDecimal(m.group(1)).stripTrailingZeros());
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
        final Set<BigDecimal> gpas = new HashSet<>();
        /** Step 16B: whole numbers written as digits in VERIFIED passages, and the last VERIFIED progress values. */
        final Set<Long> passageDigits = new HashSet<>();
        Long earned;
        Long current;
        boolean anyVerified;
        /** Step 15A: at least one VERIFIED document search. */
        boolean documentEvidence;
        /** Step 16A: at least one VERIFIED result of get_my_academic_progress. */
        boolean progressEvidence;

        /** Whether the wide credit patterns and the GPA claims apply (spec file 56, section 10.2). */
        boolean wide() { return documentEvidence || progressEvidence; }

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
                if (AcademicAiToolNames.SEARCH_UNIVERSITY_KNOWLEDGE.equals(e.toolName()) && e.status() == AcademicEvidenceStatus.VERIFIED
                        && e.verifiedData() instanceof Collection<?> passages) {
                    facts.documentEvidence = true;
                    for (Object passage : passages) if (passage instanceof Map<?, ?> p) facts.passage(p);
                }
                if (AcademicAiToolNames.GET_MY_ACADEMIC_PROGRESS.equals(e.toolName()) && e.status() == AcademicEvidenceStatus.VERIFIED
                        && e.verifiedData() instanceof Map<?, ?> progress) {
                    facts.progressEvidence = true;
                    facts.progress(progress);
                }
            }
            return facts;
        }

        // Spec file 57, section 9.2: credits derived from VERIFIED progress and VERIFIED passages of the same request.
        void derive(String answer) {
            if (!progressEvidence || !documentEvidence || answer == null || earned == null) return;
            if (current != null) credits.add(earned + current);
            Matcher m = WHOLE_NUMBER.matcher(answer);
            while (m.find()) {
                long p = Long.parseLong(m.group(1));
                if (!passageDigits.contains(p)) continue;
                credits.add(Math.abs(p - earned));
                if (current != null) credits.add(Math.abs(p - (earned + current)));
            }
        }

        // Spec file 56, section 10.1: earned credits (whole numbers only), current credits and the grade point average.
        private void progress(Map<?, ?> p) {
            BigDecimal earned = decimal(p.get("creditsEarned"));
            this.earned = earned != null && earned.stripTrailingZeros().scale() <= 0 ? earned.longValueExact() : null;
            if (this.earned != null) credits.add(this.earned);
            BigDecimal current = decimal(p.get("currentEnrolledCredits"));
            this.current = current != null && current.stripTrailingZeros().scale() <= 0 ? current.longValueExact() : null;
            if (this.current != null) credits.add(this.current);
            BigDecimal cgpa = decimal(p.get("cgpa"));
            if (cgpa != null) gpas.add(cgpa.stripTrailingZeros());
        }

        private static BigDecimal decimal(Object value) {
            if (value instanceof BigDecimal d) return d;
            if (value instanceof Number n) return new BigDecimal(n.toString());
            return null;
        }

        // Spec file 53, section 12.2: the values written in a retrieved passage.
        private void passage(Map<?, ?> p) {
            var body = new StringJoiner("\n");
            for (String key : List.of("documentTitle", "section", "text")) if (p.get(key) != null) body.add(String.valueOf(p.get(key)));
            String text = body.toString();
            var whole = new HashSet<Long>();
            Matcher m = WHOLE_NUMBER.matcher(text);
            while (m.find()) whole.add(Long.parseLong(m.group(1)));
            passageDigits.addAll(whole);
            Matcher w = NUMBER_WORDS.matcher(text);
            while (w.find()) {
                String word = w.group(1).toLowerCase(Locale.ROOT);
                whole.add((long) (WORDS.containsKey(word) ? WORDS.get(word) : ORDINALS.get(word)));
            }
            credits.addAll(whole);
            seats.addAll(whole);
            sections.addAll(whole);
            semesters.addAll(whole);
            rooms.addAll(rooms(text + "\n" + (p.get("pages") == null ? "" : p.get("pages"))));
            List<String> names = nameTokens(text);
            if (!names.isEmpty()) lecturers.add(names);
            programs.add(text.toLowerCase(Locale.ROOT));
            if (p.get("academicYear") != null) {
                Matcher y = ACADEMIC_YEAR.matcher(String.valueOf(p.get("academicYear")));
                if (y.matches()) {
                    years.add(Long.parseLong(y.group(1)));
                    years.add(Long.parseLong(y.group(2)));
                }
            }
            Matcher g = GPA_VALUE.matcher(text);
            while (g.find()) gpas.add(new BigDecimal(g.group(1)).stripTrailingZeros());
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
