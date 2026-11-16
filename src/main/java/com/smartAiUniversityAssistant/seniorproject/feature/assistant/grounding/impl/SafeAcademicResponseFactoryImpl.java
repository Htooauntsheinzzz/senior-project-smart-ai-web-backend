package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolNames.*;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidence;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.SafeAcademicResponseFactory;
import java.text.BreakIterator;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * English, deterministic replies (like the Step 7 fallback). groundedAnswer renders the fields of each verified tool
 * result with templates; a null field is written as "not currently available", never filled in. Free text entered by
 * administrators (course descriptions) is still not rendered for database results, but document passages of
 * search_university_knowledge are quoted (at most two), because the passage is the answer (Step 15A). Identical
 * evidence is rendered once.
 */
@Component
public class SafeAcademicResponseFactoryImpl implements SafeAcademicResponseFactory {
    /** The Step 7 reply when a personal question was answered without any tool evidence. */
    public static final String MISSING_EVIDENCE = "I could not retrieve your academic records for this question, "
            + "so I cannot answer it reliably. Please try again or ask more specifically, for example "
            + "\"What courses am I enrolled in?\" or \"Who teaches my CS301 class?\".";
    static final String NOT_AVAILABLE = "not currently available";
    public static final String KNOWLEDGE_NOT_FOUND = "I could not find information about this in the university documents I can "
            + "search. Please check with the responsible university office, or try asking in a different way.";
    public static final String KNOWLEDGE_UNAVAILABLE = "I can't search the university documents right now, so I won't guess. "
            + "Please try again in a few minutes.";
    public static final String KNOWLEDGE_MISSING_EVIDENCE = "I could not look this up in the university documents, so I cannot "
            + "answer it reliably. Please try asking again.";
    public static final String DEGREE_AUDIT_REFERRAL = "I could not reliably compare your records with these requirements. Please "
            + "compare the values above yourself, or ask the registrar's office for an official degree audit.";
    static final int MAX_QUOTED_PASSAGES = 2;
    static final int MAX_QUOTED_CHARS = 700;

    @Override
    public String lecturerUnavailable(String courseCode) {
        return "The lecturer for your " + course(courseCode) + "section is " + NOT_AVAILABLE + ".";
    }

    @Override
    public String roomUnavailable(String courseCode) {
        return "Your " + course(courseCode) + "classroom information is " + NOT_AVAILABLE + ".";
    }

    @Override
    public String scheduleUnavailable() {
        return "Your class schedule information is " + NOT_AVAILABLE + ".";
    }

    @Override
    public String courseNotFound(String courseIdentifier) {
        return courseIdentifier == null ? "I could not find a matching course in the university catalogue."
                : "I could not find a course matching \"" + courseIdentifier + "\" in the university catalogue.";
    }

    @Override
    public String courseEnrollmentNotFound(String courseIdentifier) {
        return "No matching enrollment was found for your account" + (courseIdentifier == null ? "" : " for " + courseIdentifier)
                + ": you are not currently enrolled in that course.";
    }

    @Override
    public String sectionNotFound(String courseSectionId) {
        return courseSectionId == null ? "I could not find that course section."
                : "I could not find a course section with id " + courseSectionId + ".";
    }

    @Override
    public String enrollmentUnavailable() {
        return "No current active enrollments were found for your account.";
    }

    @Override
    public String academicServiceUnavailable() {
        return "I can't verify your academic information right now, so I won't guess. Please try again in a few minutes.";
    }

    @Override
    public String clarificationRequired() {
        return "I found more than one matching course. Which course do you mean? Please reply with its course code.";
    }

    @Override
    public String ambiguousCourse(String courseIdentifier, List<?> candidates) {
        var lines = new ArrayList<String>();
        for (Object candidate : candidates) {
            if (!(candidate instanceof Map<?, ?> c)) continue;
            String code = text(c.get("courseCode"));
            if (code == null) continue;
            String name = text(c.get("courseName"));
            lines.add((lines.size() + 1) + ". " + code + (name == null ? "" : " — " + name));
        }
        if (lines.isEmpty()) return clarificationRequired();
        String what = courseIdentifier == null ? "more than one matching course" : "more than one course matching \"" + courseIdentifier + "\"";
        return "I found " + what + ":\n" + String.join("\n", lines) + "\nWhich course do you mean?";
    }

    @Override
    public String missingEvidence() {
        return MISSING_EVIDENCE;
    }

    @Override
    public String unsupportedScheduleReasoning() {
        return "I can't reliably work out your next class or which classes fall on a particular date, because class "
                + "times are stored as plain text in the timetable.";
    }

    @Override
    public String degreeAuditReferral() {
        return DEGREE_AUDIT_REFERRAL;
    }

    @Override
    public String knowledgeNotFound() {
        return KNOWLEDGE_NOT_FOUND;
    }

    @Override
    public String knowledgeUnavailable() {
        return KNOWLEDGE_UNAVAILABLE;
    }

    @Override
    public String knowledgeMissingEvidence() {
        return KNOWLEDGE_MISSING_EVIDENCE;
    }

    @Override
    public String groundedAnswer(List<AcademicEvidence> evidence) {
        List<AcademicEvidence> all = evidence == null ? List.of() : evidence;
        var parts = new LinkedHashSet<String>();
        for (AcademicEvidence e : all) {
            if (isKnowledge(e)) continue;
            if (e.status() == AcademicEvidenceStatus.VERIFIED_EMPTY) parts.add(enrollmentUnavailable());
            if (e.status() != AcademicEvidenceStatus.VERIFIED) continue;
            String part = render(e.toolName(), e.verifiedData());
            if (part != null) parts.add(part);
        }
        List<Map<?, ?>> passages = passages(all);
        if (!passages.isEmpty()) parts.add(passageBlock(passages));
        else if (all.stream().anyMatch(e -> isKnowledge(e) && e.status() == AcademicEvidenceStatus.UNAVAILABLE)) parts.add(knowledgeUnavailable());
        else if (all.stream().anyMatch(e -> isKnowledge(e) && e.status() == AcademicEvidenceStatus.VERIFIED_EMPTY)) parts.add(knowledgeNotFound());
        if (!parts.isEmpty()) return String.join("\n\n", parts);
        // Only searches with invalid arguments: the documents were not searched.
        if (!all.isEmpty() && all.stream().allMatch(SafeAcademicResponseFactoryImpl::isKnowledge)) return knowledgeMissingEvidence();
        // Nothing verified: a database failure is said to be temporary; otherwise (only model mistakes) nothing was retrieved.
        boolean failed = all.stream().anyMatch(e -> e.status() == AcademicEvidenceStatus.UNAVAILABLE);
        return failed ? academicServiceUnavailable() : missingEvidence();
    }

    private static boolean isKnowledge(AcademicEvidence e) {
        return SEARCH_UNIVERSITY_KNOWLEDGE.equals(e.toolName());
    }

    // The passage maps of every VERIFIED search in order, without repeats (same documentTitle, section and text).
    static List<Map<?, ?>> passages(List<AcademicEvidence> evidence) {
        var seen = new HashSet<List<Object>>();
        var out = new ArrayList<Map<?, ?>>();
        for (AcademicEvidence e : evidence) {
            if (!isKnowledge(e) || e.status() != AcademicEvidenceStatus.VERIFIED || !(e.verifiedData() instanceof List<?> rows)) continue;
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> p)) continue;
                if (seen.add(Arrays.asList(p.get("documentTitle"), p.get("section"), p.get("text")))) out.add(p);
            }
        }
        return out;
    }

    // Spec file 55, section 9: the first passage for "your program" (else the first passage), then the rest of its
    // section when it follows directly, otherwise the first other passage.
    static List<Map<?, ?>> quoted(List<Map<?, ?>> passages) {
        if (passages.size() <= 1) return passages;
        int first = 0;
        for (int i = 0; i < passages.size(); i++)
            if ("your program".equals(passages.get(i).get("appliesTo"))) {
                first = i;
                break;
            }
        Map<?, ?> head = passages.get(first);
        int second = -1;
        if (first + 1 < passages.size()) {
            Map<?, ?> next = passages.get(first + 1);
            if (Objects.equals(next.get("documentTitle"), head.get("documentTitle")) && Objects.equals(next.get("section"), head.get("section")))
                second = first + 1;
        }
        if (second < 0) second = first == 0 ? 1 : 0;
        return List.of(head, passages.get(second));
    }

    private static String passageBlock(List<Map<?, ?>> passages) {
        var out = new StringBuilder("Here is what the university documents say:");
        List<Map<?, ?>> chosen = quoted(passages);
        for (int i = 0; i < Math.min(MAX_QUOTED_PASSAGES, chosen.size()); i++) {
            Map<?, ?> p = chosen.get(i);
            out.append("\n\n").append(i + 1).append(". ").append(orUnavailable(p.get("documentTitle")));
            String year = text(p.get("academicYear"));
            if (year != null) out.append(" (").append(year).append(')');
            String appliesTo = text(p.get("appliesTo"));
            if (appliesTo != null) out.append(", for ").append(appliesTo);
            String section = text(p.get("section"));
            if (section != null) out.append(", ").append(section);
            String pages = text(p.get("pages"));
            if (pages != null) out.append(pages.contains("-") ? ", pages " : ", page ").append(pages);
            String body = text(p.get("text"));
            if (body != null) out.append('\n').append(cut(body));
        }
        return out.toString();
    }

    // At most 700 characters: cut at the last line break or space, otherwise at the last grapheme boundary.
    static String cut(String text) {
        if (text.length() <= MAX_QUOTED_CHARS) return text;
        String head = text.substring(0, MAX_QUOTED_CHARS + 1);
        int at = Math.max(head.lastIndexOf('\n'), head.lastIndexOf(' '));
        if (at > 0) return text.substring(0, at).stripTrailing() + " …";
        BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
        graphemes.setText(text);
        int end = graphemes.isBoundary(MAX_QUOTED_CHARS) ? MAX_QUOTED_CHARS : graphemes.preceding(MAX_QUOTED_CHARS);
        return text.substring(0, Math.max(end, 0)) + " …";
    }

    private String render(String tool, Object data) {
        if (data instanceof List<?> rows) {
            List<Map<?, ?>> maps = rows.stream().filter(Map.class::isInstance).<Map<?, ?>>map(r -> (Map<?, ?>) r).toList();
            return switch (tool) {
                case GET_MY_ENROLLMENTS -> list("Your current enrollments:", maps, this::enrollmentLine);
                case GET_MY_COURSES -> list("Your current courses:", maps, this::courseLine);
                case GET_MY_SCHEDULE -> list("Your recorded timetable:", maps, this::scheduleLine);
                default -> null;
            };
        }
        if (!(data instanceof Map<?, ?> m)) return null;
        return switch (tool) {
            case GET_MY_ACADEMIC_PROFILE -> profile(m);
            case GET_MY_COURSE_SECTION -> courseSection(m);
            case GET_MY_COURSE_LECTURER -> lecturer(m);
            case GET_COURSE_INFORMATION -> courseInformation(m);
            case GET_SECTION_AVAILABILITY -> availability(m);
            case GET_MY_ACADEMIC_PROGRESS -> progress(m);
            default -> null;
        };
    }

    private String profile(Map<?, ?> m) {
        String semester = text(m.get("semesterNameEn"));
        if (semester != null && text(m.get("semesterAcademicYear")) != null) semester += " (" + text(m.get("semesterAcademicYear")) + ")";
        return "Your academic profile:\n- Program: " + orUnavailable(m.get("programName"))
                + "\n- Department: " + orUnavailable(m.get("departmentName"))
                + "\n- Faculty: " + orUnavailable(m.get("facultyNameEn"))
                + "\n- Semester: " + orUnavailable(semester)
                + "\n- Academic year: " + orUnavailable(m.get("academicYear"));
    }

    // Step 16A (spec file 56, section 11): the totals as the tool returned them; nothing rounded or calculated.
    private static String progress(Map<?, ?> m) {
        return "Your academic progress:\n- Cumulative GPA: " + number(m.get("cgpa"))
                + "\n- Credits earned from recorded course results: " + number(m.get("creditsEarned"))
                + "\n- Courses this semester: " + number(m.get("currentCourses"))
                + "\n- Credits this semester: " + number(m.get("currentEnrolledCredits"))
                + "\n- Study year: " + number(m.get("studyYear"));
    }

    private static String number(Object value) {
        if (value instanceof java.math.BigDecimal d) return d.toPlainString();
        return orUnavailable(value);
    }

    private String enrollmentLine(Map<?, ?> m) {
        String status = text(m.get("enrollmentStatus"));
        return title(m) + " (section " + orUnavailable(m.get("sectionNumber")) + credits(m) + (status == null ? "" : ", " + status) + ")";
    }

    private String courseLine(Map<?, ?> m) {
        return title(m) + " (section " + orUnavailable(m.get("sectionNumber")) + credits(m) + ")";
    }

    private static String credits(Map<?, ?> m) {
        String credits = text(m.get("creditHours"));
        return credits == null ? "" : ", " + credits + " credit hours";
    }

    private String scheduleLine(Map<?, ?> m) {
        return title(m) + " (section " + orUnavailable(m.get("sectionNumber")) + "): " + meetings(m);
    }

    // Renders the published meetings of a section ("Monday 09:00-11:00 in room 11-405, Building A"), or says that the
    // timetable is not available. Only structured timetable values are written; nothing is interpreted.
    private static String meetings(Map<?, ?> m) {
        if (!(m.get("meetings") instanceof List<?> rows) || rows.isEmpty()) return "timetable " + NOT_AVAILABLE;
        var parts = new ArrayList<String>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> meeting)) continue;
            String day = text(meeting.get("dayOfWeek"));
            var part = new StringBuilder(day == null ? "day " + NOT_AVAILABLE : day.charAt(0) + day.substring(1).toLowerCase(Locale.ROOT));
            part.append(' ').append(orUnavailable(meeting.get("startTime"))).append('-').append(orUnavailable(meeting.get("endTime")));
            String type = text(meeting.get("meetingType"));
            if (type != null) part.append(" (").append(type.toLowerCase(Locale.ROOT)).append(')');
            String room = text(meeting.get("roomNumber"));
            String building = text(meeting.get("buildingName"));
            part.append(room == null ? ", room " + NOT_AVAILABLE : " in room " + room);
            if (building != null) part.append(", ").append(building);
            parts.add(part.toString());
        }
        return parts.isEmpty() ? "timetable " + NOT_AVAILABLE : String.join("; ", parts);
    }

    private String courseSection(Map<?, ?> m) {
        String code = text(m.get("courseCode"));
        var out = new StringBuilder("Your " + title(m) + " section is " + orUnavailable(m.get("sectionNumber")) + ".");
        // Rooms and class times come from the published timetable (spec 40); without meetings the classroom is unknown.
        out.append(m.get("meetings") instanceof List<?> rows && !rows.isEmpty() ? " Timetable: " + meetings(m) + "." : " " + roomUnavailable(code));
        String lecturer = text(m.get("lecturerNameEn"));
        out.append(lecturer == null ? " " + lecturerUnavailable(code) : " Lecturer: " + lecturer + ".");
        return out.toString();
    }

    private String lecturer(Map<?, ?> m) {
        String lecturer = text(m.get("lecturerNameEn"));
        if (lecturer == null) return lecturerUnavailable(text(m.get("courseCode")));
        String section = text(m.get("sectionNumber"));
        return "Your " + title(m) + (section == null ? "" : " section " + section) + " is taught by " + lecturer + ".";
    }

    private String courseInformation(Map<?, ?> m) {
        String credits = text(m.get("creditHours"));
        var out = new StringBuilder(title(m) + (credits == null ? ": credit hours are " + NOT_AVAILABLE + "."
                : " is worth " + credits + " credit hours."));
        String prerequisites = text(m.get("prerequisiteCourses"));
        out.append(prerequisites == null ? " No prerequisites are recorded." : " Prerequisites: " + prerequisites + ".");
        if (Boolean.FALSE.equals(m.get("active"))) out.append(" This course is not currently active.");
        return out.toString();
    }

    private String availability(Map<?, ?> m) {
        String course = text(m.get("courseCode"));
        String section = text(m.get("sectionNumber"));
        String capacity = text(m.get("capacity"));
        var out = new StringBuilder((course == null ? "This" : course) + " section" + (section == null ? "" : " " + section));
        if (capacity == null) out.append(": capacity is ").append(NOT_AVAILABLE).append('.');
        else out.append(" has ").append(text(m.get("availableSeats"))).append(" of ").append(capacity).append(" seats available (")
                .append(text(m.get("occupiedSeats"))).append(" taken)").append(Boolean.TRUE.equals(m.get("isFull")) ? " and is full." : " and is not full.");
        String status = text(m.get("sectionStatus"));
        if (status != null) out.append(" Section status: ").append(status).append('.');
        return out.toString();
    }

    private static String list(String heading, List<Map<?, ?>> rows, java.util.function.Function<Map<?, ?>, String> line) {
        var out = new StringBuilder(heading);
        for (int i = 0; i < rows.size(); i++) out.append('\n').append(i + 1).append(". ").append(line.apply(rows.get(i)));
        return out.toString();
    }

    private static String title(Map<?, ?> m) {
        String code = text(m.get("courseCode"));
        String name = text(m.get("courseName"));
        if (code == null) return name == null ? "course" : name;
        return name == null ? code : code + " — " + name;
    }

    private static String course(String courseCode) {
        return courseCode == null || courseCode.isBlank() ? "" : courseCode + " ";
    }

    private static String orUnavailable(Object value) {
        String text = text(value);
        return text == null ? NOT_AVAILABLE : text;
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }
}
