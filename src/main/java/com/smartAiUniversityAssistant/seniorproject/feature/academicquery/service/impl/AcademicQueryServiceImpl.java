package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.mapper.AcademicQueryMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.course.entity.Course;
import com.smartAiUniversityAssistant.seniorproject.feature.course.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.course.repository.CourseRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.CourseSectionRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception.EnrollmentFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository.EnrollmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.StudentFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import com.smartAiUniversityAssistant.seniorproject.security.CurrentStudentProvider;
import com.smartAiUniversityAssistant.seniorproject.security.PasswordChangeRequiredException;
import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes StudentAcademicService (eligibility, ACTIVE filtering and the shared fetch-join enrollment query) with
 * read-only Course and Course Section lookups. Nothing here writes; every method runs in a read-only transaction.
 */
@Service
public class AcademicQueryServiceImpl implements AcademicQueryService {
    private static final Logger log = LoggerFactory.getLogger(AcademicQueryServiceImpl.class);
    private static final String ACTIVE = EnrollmentStatus.ACTIVE.name();
    // A partial identifier matching more courses than this is treated as ambiguous without further narrowing.
    private static final int MAX_PARTIAL_MATCHES = 100;
    private static final int MAX_CANDIDATES = 10;
    private static final int MAX_IDENTIFIER_LENGTH = 255;
    // Control characters plus Unicode line and paragraph separators.
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\p{Zl}\\p{Zp}]");

    private final CurrentStudentProvider currentStudent;
    private final StudentAcademicService studentAcademics;
    private final CourseRepository courses;
    private final CourseSectionRepository sections;
    private final EnrollmentRepository enrollments;
    private final AcademicQueryMapper mapper;

    public AcademicQueryServiceImpl(CurrentStudentProvider currentStudent, StudentAcademicService studentAcademics,
            CourseRepository courses, CourseSectionRepository sections, EnrollmentRepository enrollments,
            AcademicQueryMapper mapper) {
        this.currentStudent = currentStudent;
        this.studentAcademics = studentAcademics;
        this.courses = courses;
        this.sections = sections;
        this.enrollments = enrollments;
        this.mapper = mapper;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public AcademicProfileQueryResult getMyAcademicProfile() {
        return query("get_my_academic_profile", null,
                () -> mapper.toProfileResult(studentAcademics.getProfile(currentStudent.getCurrentStudentId())));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<AcademicEnrollmentQueryResult> getMyEnrollments() {
        return query("get_my_enrollments", null, () -> studentAcademics
                .getEnrollments(currentStudent.getCurrentStudentId(), ACTIVE).stream()
                .map(mapper::toEnrollmentResult).toList());
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<AcademicCourseQueryResult> getMyCourses() {
        return query("get_my_courses", null, () -> studentAcademics
                .getCourses(currentStudent.getCurrentStudentId(), ACTIVE).stream()
                .map(mapper::toCourseResult).toList());
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<AcademicScheduleQueryResult> getMySchedule() {
        return query("get_my_schedule", null, () -> studentAcademics
                .getSchedule(currentStudent.getCurrentStudentId(), ACTIVE).stream()
                .map(mapper::toScheduleResult).toList());
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public AcademicCourseSectionQueryResult getMyCourseSection(String courseIdentifier) {
        return query("get_my_course_section", courseIdentifier, () -> {
            long studentId = currentStudent.getCurrentStudentId();
            String identifier = normalize(courseIdentifier);
            var rows = studentAcademics.getEnrollments(studentId, ACTIVE);
            return mapper.toSectionResult(resolveMine(identifier, rows, r -> r.courseId()));
        });
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public AcademicLecturerQueryResult getMyCourseLecturer(String courseIdentifier) {
        return query("get_my_course_lecturer", courseIdentifier, () -> {
            long studentId = currentStudent.getCurrentStudentId();
            String identifier = normalize(courseIdentifier);
            var rows = studentAcademics.getLecturers(studentId, ACTIVE);
            return mapper.toLecturerResult(resolveMine(identifier, rows, r -> r.courseId()));
        });
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CourseInformationQueryResult getCourseInformation(String courseIdentifier) {
        return query("get_course_information", courseIdentifier, () -> {
            long studentId = currentStudent.getCurrentStudentId();
            String identifier = normalize(courseIdentifier);
            studentAcademics.requireEligibleStudent(studentId);
            var matches = resolveCourses(identifier);
            if (matches.isEmpty()) throw new CourseNotFoundException();
            if (matches.size() > 1) throw ambiguous(matches);
            return mapper.toCourseInformationResult(matches.getFirst());
        });
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public SectionAvailabilityQueryResult getSectionAvailability(Long courseSectionId) {
        return query("get_section_availability", courseSectionId == null ? null : courseSectionId.toString(), () -> {
            long studentId = currentStudent.getCurrentStudentId();
            if (courseSectionId == null || courseSectionId <= 0) throw new AcademicQueryValidationException();
            studentAcademics.requireEligibleStudent(studentId);
            // Deleting a Course does not cascade to its Sections; a Section of a deleted Course is not live either.
            var section = sections.findByIdAndDeletedFalse(courseSectionId)
                    .filter(s -> !s.getCourse().isDeleted())
                    .orElseThrow(CourseSectionNotFoundException::new);
            long occupied = enrollments.countOccupiedSeats(section.getId(), EnrollmentStatus.SEAT_HOLDING);
            return mapper.toAvailabilityResult(section, occupied);
        });
    }

    /**
     * Resolves the identifier against the whole catalogue first, so an exact match on a course the Student is not
     * taking is reported as not enrolled rather than partially matched to a different enrolled course. Among several
     * catalogue matches, the Student's own ACTIVE enrollments decide; rows are newest semester first.
     */
    private <T> T resolveMine(String identifier, List<T> rows, Function<T, Long> courseId) {
        var matches = resolveCourses(identifier);
        if (matches.isEmpty()) throw new CourseNotFoundException();
        if (matches.size() > MAX_PARTIAL_MATCHES) throw ambiguous(matches);
        var matchedIds = new HashSet<Long>();
        matches.forEach(c -> matchedIds.add(c.getId()));
        var mine = rows.stream().filter(r -> matchedIds.contains(courseId.apply(r))).toList();
        if (mine.isEmpty()) throw new StudentCourseEnrollmentNotFoundException();
        var enrolledIds = new LinkedHashSet<Long>();
        mine.forEach(r -> enrolledIds.add(courseId.apply(r)));
        if (enrolledIds.size() > 1) throw ambiguous(matches.stream().filter(c -> enrolledIds.contains(c.getId())).toList());
        return mine.getFirst();
    }

    // Stops at the first stage with any match, so an exact match is never mixed with looser ones.
    private List<Course> resolveCourses(String identifier) {
        List<Supplier<List<Course>>> stages = List.of(
                () -> courses.findByCourseCodeAndDeletedFalse(identifier),
                () -> courses.findByCourseNameAndDeletedFalse(identifier),
                () -> courses.findByCourseCodeIgnoreCaseAndDeletedFalse(identifier),
                () -> courses.findByCourseNameIgnoreCaseAndDeletedFalse(identifier),
                () -> courses.findPartialMatches("%" + escapeLike(identifier.toLowerCase(Locale.ROOT)) + "%",
                        Pageable.ofSize(MAX_PARTIAL_MATCHES + 1)));
        for (var stage : stages) {
            var matches = stage.get();
            if (!matches.isEmpty()) return matches;
        }
        return List.of();
    }

    private AcademicCourseAmbiguousException ambiguous(List<Course> matches) {
        return new AcademicCourseAmbiguousException(matches.stream()
                .sorted(Comparator.comparing(Course::getCourseCode).thenComparing(Course::getId))
                .limit(MAX_CANDIDATES).map(mapper::toCandidate).toList());
    }

    private static String normalize(String identifier) {
        String value = identifier == null ? "" : identifier.strip();
        // Control characters (including NUL, which PostgreSQL rejects in text) never appear in course codes or names.
        if (value.isEmpty() || value.length() > MAX_IDENTIFIER_LENGTH || CONTROL.matcher(value).find())
            throw new AcademicQueryValidationException();
        return value;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // Logs one line per tool call (tool, argument, outcome, timing) for later assistant research metrics.
    private <T> T query(String toolName, String argument, Supplier<T> body) {
        long started = System.nanoTime();
        try {
            T result = body.get();
            log.info("ACADEMIC_QUERY tool={} argument={} success=true resultCount={} executionTimeMs={}", toolName,
                    loggable(argument), result instanceof Collection<?> c ? c.size() : 1, elapsedMs(started));
            return result;
        } catch (RuntimeException e) {
            log.info("ACADEMIC_QUERY tool={} argument={} success=false errorCode={} executionTimeMs={}", toolName,
                    loggable(argument), errorCode(e), elapsedMs(started));
            throw e;
        }
    }

    private static long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }

    // Course identifiers are not sensitive, but they come from the model: strip control characters and cap length.
    private static String loggable(String argument) {
        if (argument == null) return "-";
        String clean = CONTROL.matcher(argument).replaceAll(" ").strip();
        return clean.length() > 100 ? clean.substring(0, 100) + "..." : clean;
    }

    private static String errorCode(RuntimeException e) {
        if (e instanceof AcademicQueryFailure f) return f.code();
        if (e instanceof CourseFailure f) return f.code();
        if (e instanceof CourseSectionFailure f) return f.code();
        if (e instanceof StudentFailure f) return f.code();
        if (e instanceof EnrollmentFailure f) return f.code();
        if (e instanceof AuthenticationFailure f) return f.code();
        if (e instanceof PasswordChangeRequiredException) return "PASSWORD_CHANGE_REQUIRED";
        if (e instanceof AccessDeniedException) return "FORBIDDEN";
        return e.getClass().getSimpleName();
    }
}
