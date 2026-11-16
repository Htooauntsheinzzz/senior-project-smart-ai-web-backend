package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.StudentAcademicProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.course.enums.CourseType;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSection;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSectionMeeting;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseSectionStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.CourseSectionNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.CourseSectionRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.entity.Enrollment;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository.EnrollmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service.EnrollmentWindowService;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service.StudentEnrollmentService;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.Student;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.StudentNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.student.repository.StudentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.mapper.StudentAcademicMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.StudentTimetableService;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student self-enrollment (spec file 43). A request starts as PENDING. The Student locks their enrollment to make
 * every PENDING request ACTIVE (an admin may still activate one through the admin API); the Student may withdraw
 * their own PENDING or ACTIVE Enrollment. Capacity and duplicate rules are the admin workflow's (spec file 26).
 */
@Service
public class StudentEnrollmentServiceImpl implements StudentEnrollmentService {
    static final String ALREADY_ENROLLED = "ALREADY_ENROLLED";
    static final String ALREADY_ENROLLED_IN_COURSE = "ALREADY_ENROLLED_IN_COURSE";
    static final String ENROLLMENT_CLOSED = "ENROLLMENT_CLOSED";
    static final String SECTION_FULL = "SECTION_FULL";
    static final String DROPPED_BY_UNIVERSITY = "DROPPED_BY_UNIVERSITY";

    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final CourseSectionRepository sections;
    private final StudentAcademicService academics;
    private final StudentTimetableService timetable;
    private final StudentAcademicMapper mapper;
    private final StudentAcademicProperties properties;
    private final EnrollmentWindowService windows;
    private final Clock clock;
    private final EntityManager entityManager;

    public StudentEnrollmentServiceImpl(EnrollmentRepository enrollments, StudentRepository students,
            CourseSectionRepository sections, StudentAcademicService academics, StudentTimetableService timetable,
            StudentAcademicMapper mapper, StudentAcademicProperties properties, EnrollmentWindowService windows, Clock clock,
            EntityManager entityManager) {
        this.enrollments = enrollments;
        this.students = students;
        this.sections = sections;
        this.academics = academics;
        this.timetable = timetable;
        this.mapper = mapper;
        this.properties = properties;
        this.windows = windows;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<StudentAvailableSectionResponse> availableSections(long studentId) {
        academics.requireEligibleStudent(studentId);
        Student student = students.findByIdAndDeletedFalse(studentId).orElseThrow(StudentNotFoundException::new);
        // The Student's own semester is the only verified "current term" (spec file 40).
        if (student.getSemester() == null) return List.of();
        List<CourseSection> candidates = sections.findOpenForSelfEnrollment(student.getSemester().getId());
        // Only sections that are on the published timetable are offered (spec file 44).
        Map<Long, EnrollmentWindow> window = windows.windows(candidates.stream().map(CourseSection::getId).toList());
        List<CourseSection> open = candidates.stream().filter(s -> window.get(s.getId()).scheduled()).toList();
        if (open.isEmpty()) return List.of();
        List<Long> ids = open.stream().map(CourseSection::getId).toList();
        Map<Long, Long> occupied = enrollments.countOccupiedSeats(ids, EnrollmentStatus.SEAT_HOLDING).stream()
                .collect(Collectors.toMap(EnrollmentRepository.SectionCount::getSectionId, EnrollmentRepository.SectionCount::getTotal));
        List<Enrollment> mine = enrollments.findStudentAcademicEnrollments(studentId, EnumSet.allOf(EnrollmentStatus.class));
        Map<Long, Enrollment> bySection = mine.stream().collect(Collectors.toMap(e -> e.getCourseSection().getId(), e -> e));
        // A seat-holding Enrollment blocks every other section of the same Course in the same Semester.
        Set<String> heldCourses = mine.stream().filter(e -> e.getStatus().holdsSeat())
                .map(e -> courseKey(e.getCourseSection())).collect(Collectors.toSet());
        Map<Long, List<StudentCourseMeetingResponse>> meetings = meetings(ids);
        return open.stream().map(section -> {
            Enrollment own = bySection.get(section.getId());
            long taken = occupied.getOrDefault(section.getId(), 0L);
            boolean full = taken >= section.getCapacity();
            String blocked = own != null && own.getStatus().holdsSeat() ? ALREADY_ENROLLED
                    : own != null && own.getStatus() == EnrollmentStatus.DROPPED ? DROPPED_BY_UNIVERSITY
                    : heldCourses.contains(courseKey(section)) ? ALREADY_ENROLLED_IN_COURSE
                    : window.get(section.getId()).closed() ? ENROLLMENT_CLOSED
                    : full ? SECTION_FULL : null;
            var course = section.getCourse();
            var semester = section.getSemester();
            var lecture = section.getLecture();
            return new StudentAvailableSectionResponse(section.getId(), section.getSectionNumber(),
                    course.getId(), course.getCourseCode(), course.getCourseName(), course.getCreditHours(),
                    course.getDescription(), course.getCourseType().intValue(),
                    CourseType.fromCode(course.getCourseType()).getDisplayName(),
                    semester.getId(), semester.getAcademicYear(), semester.getSemesterNameEn(), semester.getSemesterNameTh(),
                    lecture.getId(), lecture.getLectureNameEn(), lecture.getLectureNameTh(),
                    section.getCapacity(), taken, Math.max(0, section.getCapacity() - taken), full,
                    own == null ? null : own.getId(), own == null ? null : own.getStatus().name(),
                    blocked == null, blocked, window.get(section.getId()).dueDate(), window.get(section.getId()).closed(), meetings.getOrDefault(section.getId(), List.of()));
        }).toList();
    }

    @Override @Transactional(timeout = 15)
    public StudentAcademicEnrollmentResponse enroll(long studentId, StudentEnrollRequest request) {
        academics.requireEligibleStudent(studentId);
        lockTimeout();
        // Same lock order as the admin workflow: Student, then Course Section.
        Student student = students.findByIdForUpdate(studentId).orElseThrow(StudentNotFoundException::new);
        CourseSection section = sections.findByIdForUpdate(request.courseSectionId())
                .orElseThrow(CourseSectionNotFoundException::new);
        if (section.isDeleted()) throw new CourseSectionNotFoundException();
        var course = section.getCourse();
        if (!CourseSectionStatus.ACTIVE.getValue().equals(section.getStatus()) || course.isDeleted() || !course.isActive()
                || student.getSemester() == null || !student.getSemester().getId().equals(section.getSemester().getId()))
            throw new CourseSectionNotAvailableException();
        Enrollment enrollment = enrollments.findByStudentIdAndCourseSectionId(studentId, section.getId()).orElse(null);
        if (enrollment != null && !enrollment.isDeleted()) {
            if (enrollment.getStatus().holdsSeat()) throw new EnrollmentAlreadyExistsException();
            // DROPPED is the university's decision; only the Student's own withdrawal can be requested again.
            if (enrollment.getStatus() == EnrollmentStatus.DROPPED)
                throw new EnrollmentFailure(409, "ENROLLMENT_DROPPED_BY_UNIVERSITY",
                        "This enrollment was dropped by the university. Please contact the registrar's office.");
        }
        long excludeId = enrollment == null ? 0L : enrollment.getId();
        if (enrollments.countConflictingEnrollments(studentId, course.getId(), section.getSemester().getId(),
                EnrollmentStatus.SEAT_HOLDING, excludeId) > 0) throw new StudentAlreadyEnrolledInCourseException();
        windows.requireOpen(section.getId());
        if (enrollments.countOccupiedSeats(section.getId(), EnrollmentStatus.SEAT_HOLDING) >= section.getCapacity())
            throw new CourseSectionFullException();
        if (enrollment == null) {
            enrollment = new Enrollment();
            enrollment.setStudent(student);
            enrollment.setCourseSection(section);
            enrollment.setCreatedAt(now());
        } else {
            // UNIQUE(student_id, course_section_id) keeps one lifecycle per pair, so the old row is requested again.
            enrollment.setUpdatedAt(now());
        }
        enrollment.setEnrollmentDate(LocalDate.ofInstant(clock.instant(), properties.timezone()));
        enrollment.setStatus(EnrollmentStatus.PENDING);
        enrollment.setDeleted(false);
        try {
            enrollments.saveAndFlush(enrollment);
        } catch (DataIntegrityViolationException e) {
            throw new EnrollmentAlreadyExistsException();
        }
        return response(enrollment);
    }

    @Override @Transactional(timeout = 15)
    public StudentAcademicEnrollmentResponse withdraw(long studentId, long enrollmentId) {
        academics.requireEligibleStudent(studentId);
        lockTimeout();
        Enrollment enrollment = enrollments.findByIdForUpdate(enrollmentId).orElseThrow(EnrollmentNotFoundException::new);
        // Another Student's Enrollment is reported exactly like a missing one.
        if (enrollment.isDeleted() || !enrollment.getStudent().getId().equals(studentId))
            throw new EnrollmentNotFoundException();
        if (enrollment.getStatus() == EnrollmentStatus.WITHDRAWN) return response(enrollment);
        if (!enrollment.getStatus().canTransitionTo(EnrollmentStatus.WITHDRAWN))
            throw new InvalidEnrollmentStatusTransitionException();
        if (enrollments.hasCourseResult(enrollmentId)) throw new EnrollmentFailure(409, "ENROLLMENT_HAS_COURSE_RESULT",
                "An enrollment with a recorded course result cannot be withdrawn.");
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setUpdatedAt(now());
        enrollments.flush();
        return response(enrollment);
    }

    @Override @Transactional(timeout = 15)
    public List<StudentAcademicEnrollmentResponse> lock(long studentId) {
        academics.requireEligibleStudent(studentId);
        lockTimeout();
        Student student = students.findByIdForUpdate(studentId).orElseThrow(StudentNotFoundException::new);
        if (student.getSemester() == null) throw new EnrollmentFailure(409, "NO_PENDING_ENROLLMENTS",
                "You have no pending enrollment requests to lock.");
        Long semesterId = student.getSemester().getId();
        List<Enrollment> pending = enrollments.findStudentAcademicEnrollments(studentId, EnumSet.of(EnrollmentStatus.PENDING))
                .stream().filter(e -> semesterId.equals(e.getCourseSection().getSemester().getId())).toList();
        if (pending.isEmpty()) throw new EnrollmentFailure(409, "NO_PENDING_ENROLLMENTS",
                "You have no pending enrollment requests to lock.");
        var now = now();
        List<Enrollment> locked = new ArrayList<>();
        // All or nothing: a section closed since the request fails the whole lock and names the course.
        for (Enrollment e : pending.stream().sorted(Comparator.comparing(Enrollment::getId)).toList()) {
            Enrollment row = enrollments.findByIdForUpdate(e.getId()).orElseThrow(EnrollmentNotFoundException::new);
            if (row.isDeleted() || row.getStatus() != EnrollmentStatus.PENDING) continue;
            activate(row, now);
            locked.add(row);
        }
        enrollments.flush();
        return locked.stream().map(this::response).toList();
    }

    @Override @Transactional(timeout = 15)
    public StudentAcademicEnrollmentResponse lockOne(long studentId, long enrollmentId) {
        academics.requireEligibleStudent(studentId);
        lockTimeout();
        students.findByIdForUpdate(studentId).orElseThrow(StudentNotFoundException::new);
        Enrollment row = enrollments.findByIdForUpdate(enrollmentId).orElseThrow(EnrollmentNotFoundException::new);
        // Another Student's Enrollment is reported exactly like a missing one.
        if (row.isDeleted() || !row.getStudent().getId().equals(studentId)) throw new EnrollmentNotFoundException();
        if (row.getStatus() == EnrollmentStatus.ACTIVE) return response(row);
        if (row.getStatus() != EnrollmentStatus.PENDING) throw new EnrollmentFailure(409, "ENROLLMENT_NOT_PENDING",
                "Only a pending request can be locked.");
        activate(row, now());
        enrollments.flush();
        return response(row);
    }

    // A PENDING row already holds its seat and passed the same-course check, so only a closed section can block it.
    private void activate(Enrollment row, LocalDateTime now) {
        CourseSection section = sections.findByIdForUpdate(row.getCourseSection().getId())
                .orElseThrow(CourseSectionNotFoundException::new);
        if (section.isDeleted() || !CourseSectionStatus.ACTIVE.getValue().equals(section.getStatus()))
            throw new EnrollmentFailure(409, "ENROLLMENT_LOCK_SECTION_CLOSED",
                    section.getCourse().getCourseCode() + " section " + section.getSectionNumber()
                            + " is no longer open. Withdraw that request instead.");
        row.setStatus(EnrollmentStatus.ACTIVE);
        row.setUpdatedAt(now);
    }

    private StudentAcademicEnrollmentResponse response(Enrollment enrollment) {
        Long sectionId = enrollment.getCourseSection().getId();
        return mapper.enrollment(enrollment, meetings(List.of(sectionId)).getOrDefault(sectionId, List.of()));
    }

    private Map<Long, List<StudentCourseMeetingResponse>> meetings(Collection<Long> sectionIds) {
        List<CourseSectionMeeting> published = timetable.publishedMeetings(new HashSet<>(sectionIds));
        var exceptions = timetable.upcomingExceptions(published.stream().map(CourseSectionMeeting::getId).toList(),
                LocalDate.ofInstant(clock.instant(), properties.timezone()));
        Map<Long, List<StudentCourseMeetingResponse>> bySection = new HashMap<>();
        published.forEach(m -> bySection.computeIfAbsent(m.getCourseSection().getId(), id -> new ArrayList<>())
                .add(mapper.meeting(m, exceptions.getOrDefault(m.getId(), List.of()))));
        return bySection;
    }

    private static String courseKey(CourseSection section) {
        return section.getCourse().getId() + ":" + section.getSemester().getId();
    }
    private void lockTimeout() { entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
