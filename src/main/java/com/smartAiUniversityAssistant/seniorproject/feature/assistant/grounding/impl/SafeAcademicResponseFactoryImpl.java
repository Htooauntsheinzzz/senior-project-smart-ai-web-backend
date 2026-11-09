package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolNames.*;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidence;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.SafeAcademicResponseFactory;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * English, deterministic replies (like the Step 7 fallback). groundedAnswer renders the fields of each verified tool
 * result with templates; a null field is written as "not currently available", never filled in. Free text entered by
 * administrators (course descriptions) is not rendered. Identical evidence is rendered once.
 */
@Component
public class SafeAcademicResponseFactoryImpl implements SafeAcademicResponseFactory {
    /** The Step 7 reply when a personal question was answered without any tool evidence. */
    public static final String MISSING_EVIDENCE = "I could not retrieve your academic records for this question, "
            + "so I cannot answer it reliably. Please try again or ask more specifically, for example "
            + "\"What courses am I enrolled in?\" or \"Who teaches my CS301 class?\".";
    static final String NOT_AVAILABLE = "not currently available";

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
    public String groundedAnswer(List<AcademicEvidence> evidence) {
        var parts = new LinkedHashSet<String>();
        for (AcademicEvidence e : evidence == null ? List.<AcademicEvidence>of() : evidence) {
            if (e.status() == AcademicEvidenceStatus.VERIFIED_EMPTY) parts.add(enrollmentUnavailable());
            if (e.status() != AcademicEvidenceStatus.VERIFIED) continue;
            String part = render(e.toolName(), e.verifiedData());
            if (part != null) parts.add(part);
        }
        if (!parts.isEmpty()) return String.join("\n\n", parts);
        // Nothing verified: a database failure is said to be temporary; otherwise (only model mistakes) nothing was retrieved.
        boolean failed = evidence != null && evidence.stream().anyMatch(e -> e.status() == AcademicEvidenceStatus.UNAVAILABLE);
        return failed ? academicServiceUnavailable() : missingEvidence();
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
