package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.course.entity.Course;
import com.smartAiUniversityAssistant.seniorproject.feature.course.exception.CourseNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.course.repository.CourseRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSection;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseSectionStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.mapper.CourseSectionMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.CourseSectionRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.service.CourseSectionService;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository.EnrollmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.entity.Lecture;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.exception.LectureNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.repository.LectureRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.exception.SemesterNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.repository.SemesterRepository;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CourseSectionServiceImpl implements CourseSectionService {
    private final CourseSectionRepository sections;
    private final CourseRepository courses;
    private final LectureRepository lectures;
    private final SemesterRepository semesters;
    private final EnrollmentRepository enrollments;
    private final CourseSectionMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public CourseSectionServiceImpl(CourseSectionRepository sections, CourseRepository courses,
            LectureRepository lectures, SemesterRepository semesters, EnrollmentRepository enrollments,
            CourseSectionMapper mapper, Clock clock, EntityManager entityManager) {
        this.sections = sections;
        this.courses = courses;
        this.lectures = lectures;
        this.semesters = semesters;
        this.enrollments = enrollments;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(timeout = 15)
    public CourseSectionResponse create(AuthenticatedUser actor, CourseSectionCreateRequest request) {
        requireFull(actor);
        lockTimeout();
        Course course = selectedCourse(request.courseId());
        Lecture lecture = selectedLecture(request.lectureId());
        requireAcademicMatch(lecture, course);
        Semester semester = selectedSemester(request.semesterId());
        String status = CourseSectionStatus.fromValue(request.status()).getValue();
        Integer capacity = capacity(request.capacity(), course);
        duplicates(0L, course.getId(), request.sectionNumber(), semester.getId());
        var section = new CourseSection();
        section.setCourse(course);
        section.setSectionNumber(request.sectionNumber());
        section.setCapacity(capacity);
        section.setLecture(lecture);
        section.setRoom(request.room());
        section.setSchedule(request.schedule());
        section.setSemester(semester);
        section.setStatus(status);
        section.setDeleted(false);
        section.setCreatedBy(actor.userId());
        section.setCreatedAt(now());
        try {
            return mapper.response(sections.saveAndFlush(section), 0);
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public CourseSectionPageResponse list(AuthenticatedUser actor, CourseSectionListQuery query) {
        requireFull(actor);
        var page = sections.findAll(specification(query), query.pageable());
        var enrolled = new HashMap<Long, Long>();
        if (!page.isEmpty())
            enrollments.countOccupiedSeats(page.getContent().stream().map(CourseSection::getId).toList(),
                    EnrollmentStatus.SEAT_HOLDING).forEach(row -> enrolled.put(row.getSectionId(), row.getTotal()));
        return mapper.page(page, query, enrolled);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CourseSectionResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        CourseSection section = sections.findByIdAndDeletedFalse(id).orElseThrow(CourseSectionNotFoundException::new);
        return mapper.response(section, enrolled(id));
    }

    @Override @Transactional(timeout = 15)
    public CourseSectionResponse update(AuthenticatedUser actor, long id, CourseSectionUpdateRequest request) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        CourseSection section = locked(id);
        Course course = selectedCourse(request.courseId());
        Lecture lecture = selectedLecture(request.lectureId());
        requireAcademicMatch(lecture, course);
        Semester semester = selectedSemester(request.semesterId());
        String status = CourseSectionStatus.fromValue(request.status()).getValue();
        duplicates(id, course.getId(), request.sectionNumber(), semester.getId());
        // The Section row lock blocks concurrent enrollments, so this count is stable until commit.
        long enrolled = enrolled(id);
        if (request.capacity() < enrolled) throw new CourseSectionFailure(409, "COURSE_SECTION_CAPACITY_BELOW_ENROLLED",
                "Capacity cannot be lower than the number of students already enrolled in this section.");
        section.setCourse(course);
        section.setSectionNumber(request.sectionNumber());
        section.setCapacity(request.capacity());
        section.setLecture(lecture);
        section.setRoom(request.room());
        section.setSchedule(request.schedule());
        section.setSemester(semester);
        section.setStatus(status);
        section.setUpdatedBy(actor.userId());
        section.setUpdatedAt(now());
        try {
            sections.flush();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return mapper.response(section, enrolled);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        CourseSection section = locked(id);
        section.setDeleted(true);
        section.setUpdatedBy(actor.userId());
        section.setUpdatedAt(now());
        sections.flush();
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public CourseSectionSummaryResponse summary(AuthenticatedUser actor) {
        requireFull(actor);
        long total = sections.countByDeletedFalse();
        long active = sections.countByStatusAndDeletedFalse(CourseSectionStatus.ACTIVE.getValue());
        long closed = sections.countByStatusAndDeletedFalse(CourseSectionStatus.CLOSED.getValue());
        long full = enrollments.countFullSections(EnrollmentStatus.SEAT_HOLDING);
        long totalEnrolled = enrollments.countOccupiedSeatsInLiveSections(EnrollmentStatus.SEAT_HOLDING);
        return new CourseSectionSummaryResponse(total, active, closed, full, totalEnrolled);
    }

    private CourseSection locked(long id) {
        CourseSection section = sections.findByIdForUpdate(id).orElseThrow(CourseSectionNotFoundException::new);
        if (section.isDeleted()) throw new CourseSectionNotFoundException();
        return section;
    }

    private long enrolled(long sectionId) {
        return enrollments.countOccupiedSeats(sectionId, EnrollmentStatus.SEAT_HOLDING);
    }

    private Course selectedCourse(Long id) {
        Course course = courses.findByIdForUpdate(id).orElseThrow(CourseNotFoundException::new);
        if (course.isDeleted()) throw new CourseNotFoundException();
        if (!course.isActive()) throw new InvalidCourseSectionCourseException();
        return course;
    }

    private Lecture selectedLecture(Long id) {
        Lecture lecture = lectures.findByIdForUpdate(id).orElseThrow(LectureNotFoundException::new);
        if (lecture.isDeleted()) throw new LectureNotFoundException();
        if (!CourseSectionStatus.ACTIVE.getValue().equals(lecture.getStatus()))
            throw new InvalidCourseSectionLecturerException();
        return lecture;
    }

    private void requireAcademicMatch(Lecture lecture, Course course) {
        if (!lecture.getFaculty().getId().equals(course.getFaculty().getId())
                || !lecture.getDepartment().getId().equals(course.getDepartment().getId()))
            throw new InvalidCourseSectionLecturerException();
    }

    private Semester selectedSemester(Long id) {
        Semester semester = semesters.findByIdForUpdate(id).orElseThrow(SemesterNotFoundException::new);
        if (semester.isDeleted()) throw new SemesterNotFoundException();
        return semester;
    }

    private Integer capacity(Integer requested, Course course) {
        // An omitted capacity falls back to the course's per-section default.
        Integer capacity = requested != null ? requested : course.getMaximumStudentsPerSection();
        if (capacity == null || capacity <= 0)
            throw new CourseSectionFailure(400, "VALIDATION_ERROR", "The request is invalid.");
        return capacity;
    }

    private void duplicates(long id, Long courseId, String sectionNumber, Long semesterId) {
        // The scoped UNIQUE constraint reserves section numbers even after soft deletion.
        if (sections.existsByCourseIdAndSectionNumberAndSemesterIdAndIdNot(courseId, sectionNumber, semesterId, id))
            throw new CourseSectionAlreadyExistsException();
    }

    private Specification<CourseSection> specification(CourseSectionListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.courseId() != null) predicates.add(cb.equal(root.get("course").get("id"), query.courseId()));
            if (query.semesterId() != null)
                predicates.add(cb.equal(root.get("semester").get("id"), query.semesterId()));
            if (query.lectureId() != null)
                predicates.add(cb.equal(root.get("lecture").get("id"), query.lectureId()));
            if (query.status() != null) predicates.add(cb.equal(root.get("status"), query.status()));
            if (query.search() != null) {
                String literal = query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.or(cb.like(cb.lower(root.get("course").get("courseCode")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("course").get("courseName")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("sectionNumber")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("lecture").get("lectureNameEn")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("lecture").get("lectureNameTh")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("lecture").get("lectureNickname")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("room")), "%" + literal + "%", '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uk_course_sections_course_section_semester".equals(violation.getConstraintName()))
                return new CourseSectionAlreadyExistsException();
        }
        return exception;
    }

    private void lockTimeout() { entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
    private void positiveId(long id) {
        if (id <= 0) throw new CourseSectionFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
