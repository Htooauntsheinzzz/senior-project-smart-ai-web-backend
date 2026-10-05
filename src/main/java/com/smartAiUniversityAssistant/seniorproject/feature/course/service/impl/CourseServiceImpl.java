package com.smartAiUniversityAssistant.seniorproject.feature.course.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.course.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.course.entity.Course;
import com.smartAiUniversityAssistant.seniorproject.feature.course.enums.CourseType;
import com.smartAiUniversityAssistant.seniorproject.feature.course.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.course.mapper.CourseMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.course.repository.CourseRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.course.service.CourseService;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.CourseSectionRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.department.exception.DepartmentNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.department.exception.InvalidFacultyException;
import com.smartAiUniversityAssistant.seniorproject.feature.department.repository.DepartmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.exception.FacultyNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.repository.FacultyRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import com.smartAiUniversityAssistant.seniorproject.feature.program.exception.ProgramNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.program.repository.ProgramRepository;
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
public class CourseServiceImpl implements CourseService {
    private final CourseRepository courses;
    private final FacultyRepository faculties;
    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    private final SemesterRepository semesters;
    private final CourseSectionRepository sections;
    private final CourseMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public CourseServiceImpl(CourseRepository courses, FacultyRepository faculties, DepartmentRepository departments,
            ProgramRepository programs, SemesterRepository semesters, CourseSectionRepository sections,
            CourseMapper mapper, Clock clock, EntityManager entityManager) {
        this.courses = courses;
        this.faculties = faculties;
        this.departments = departments;
        this.programs = programs;
        this.semesters = semesters;
        this.sections = sections;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(timeout = 15)
    public CourseResponse create(AuthenticatedUser actor, CourseCreateRequest request) {
        requireFull(actor);
        lockTimeout();
        duplicates(0L, request.courseCode());
        Faculty faculty = selectedFaculty(request.facultyId());
        Department department = selectedDepartment(request.departmentId());
        requireFacultyMatch(department, faculty);
        Program program = selectedProgram(request.programId());
        requireDepartmentMatch(program, department);
        Semester semester = selectedSemester(request.semesterId());
        CourseType type = CourseType.fromCode(request.courseType());
        var course = new Course();
        course.setCourseCode(request.courseCode());
        course.setCourseName(request.courseName());
        course.setCreditHours(request.creditHours());
        course.setDescription(request.description());
        course.setFaculty(faculty);
        course.setDepartment(department);
        course.setProgram(program);
        course.setSemester(semester);
        course.setRecommendedAcademicYear(shortValue(request.recommendedAcademicYear()));
        course.setPrerequisiteCourses(prerequisites(request.prerequisiteCourses(), request.courseCode()));
        course.setCourseType((short) type.getCode());
        course.setMaximumStudentsPerSection(request.maximumStudentsPerSection());
        course.setActive(request.isActive() == null || request.isActive());
        course.setDeleted(false);
        course.setCreatedBy(actor.userId());
        course.setCreatedAt(now());
        try {
            return mapper.response(courses.saveAndFlush(course));
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public CoursePageResponse list(AuthenticatedUser actor, CourseListQuery query) {
        requireFull(actor);
        var page = courses.findAll(specification(query), query.pageable());
        var sectionCounts = sectionCountsByCourse(page.getContent().stream().map(Course::getId).toList());
        return mapper.page(page, query, sectionCounts);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CourseResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        return mapper.response(courses.findByIdAndDeletedFalse(id).orElseThrow(CourseNotFoundException::new));
    }

    @Override @Transactional(timeout = 15)
    public CourseResponse update(AuthenticatedUser actor, long id, CourseUpdateRequest request) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Course course = locked(id);
        duplicates(id, request.courseCode());
        Faculty faculty = selectedFaculty(request.facultyId());
        Department department = selectedDepartment(request.departmentId());
        requireFacultyMatch(department, faculty);
        Program program = selectedProgram(request.programId());
        requireDepartmentMatch(program, department);
        Semester semester = selectedSemester(request.semesterId());
        CourseType type = CourseType.fromCode(request.courseType());
        course.setCourseCode(request.courseCode());
        course.setCourseName(request.courseName());
        course.setCreditHours(request.creditHours());
        course.setDescription(request.description());
        course.setFaculty(faculty);
        course.setDepartment(department);
        course.setProgram(program);
        course.setSemester(semester);
        course.setRecommendedAcademicYear(shortValue(request.recommendedAcademicYear()));
        course.setPrerequisiteCourses(prerequisites(request.prerequisiteCourses(), request.courseCode()));
        course.setCourseType((short) type.getCode());
        course.setMaximumStudentsPerSection(request.maximumStudentsPerSection());
        course.setActive(request.isActive());
        course.setUpdatedBy(actor.userId());
        course.setUpdatedAt(now());
        try {
            courses.flush();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return mapper.response(course);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Course course = locked(id);
        course.setDeleted(true);
        course.setUpdatedBy(actor.userId());
        course.setUpdatedAt(now());
        courses.flush();
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public CourseSummaryResponse summary(AuthenticatedUser actor) {
        requireFull(actor);
        return new CourseSummaryResponse(courses.countByDeletedFalse(), courses.countByActiveTrueAndDeletedFalse(),
                courses.countByActiveFalseAndDeletedFalse(), 0);
    }

    private Course locked(long id) {
        Course course = courses.findByIdForUpdate(id).orElseThrow(CourseNotFoundException::new);
        if (course.isDeleted()) throw new CourseNotFoundException();
        return course;
    }

    private Faculty selectedFaculty(Long id) {
        Faculty faculty = faculties.findByIdForUpdate(id).orElseThrow(FacultyNotFoundException::new);
        if (faculty.isDeleted()) throw new FacultyNotFoundException();
        if (!faculty.isActive()) throw new InvalidFacultyException();
        return faculty;
    }

    private Department selectedDepartment(Long id) {
        Department department = departments.findByIdForUpdate(id).orElseThrow(DepartmentNotFoundException::new);
        if (department.isDeleted()) throw new DepartmentNotFoundException();
        if (!department.isActive())
            throw new InvalidCourseRelationshipException("The selected department is inactive.");
        return department;
    }

    private Program selectedProgram(Long id) {
        if (id == null) return null;
        Program program = programs.findByIdForUpdate(id).orElseThrow(ProgramNotFoundException::new);
        if (program.isDeleted()) throw new ProgramNotFoundException();
        if (!program.isActive())
            throw new InvalidCourseRelationshipException("The selected program is inactive.");
        return program;
    }

    private Semester selectedSemester(Long id) {
        if (id == null) return null;
        Semester semester = semesters.findByIdForUpdate(id).orElseThrow(SemesterNotFoundException::new);
        if (semester.isDeleted()) throw new SemesterNotFoundException();
        return semester;
    }

    private void requireFacultyMatch(Department department, Faculty faculty) {
        if (!department.getFaculty().getId().equals(faculty.getId()))
            throw new InvalidCourseRelationshipException("The selected department does not belong to the selected faculty.");
    }

    private void requireDepartmentMatch(Program program, Department department) {
        if (program != null && !program.getDepartment().getId().equals(department.getId()))
            throw new InvalidCourseRelationshipException("The selected program does not belong to the selected department.");
    }

    private String prerequisites(String raw, String selfCode) {
        if (raw == null) return null;
        var codes = new LinkedHashSet<String>();
        for (String part : raw.split(",")) {
            String code = part.trim();
            if (!code.isEmpty()) codes.add(code);
        }
        if (codes.isEmpty()) return null;
        for (String code : codes) {
            if (code.equals(selfCode))
                throw new InvalidPrerequisiteCourseException("A course cannot be its own prerequisite.");
            if (!courses.existsByCourseCodeAndDeletedFalse(code))
                throw new InvalidPrerequisiteCourseException("Prerequisite course " + code + " does not exist.");
        }
        return String.join(",", codes);
    }

    private void duplicates(long id, String courseCode) {
        // The UNIQUE constraint reserves course codes even after soft deletion.
        if (courses.existsByCourseCodeAndIdNot(courseCode, id)) throw new CourseCodeAlreadyExistsException();
    }

    private Map<Long, Long> sectionCountsByCourse(Collection<Long> courseIds) {
        if (courseIds.isEmpty()) return Map.of();
        var result = new HashMap<Long, Long>();
        sections.countForCourses(courseIds).forEach(row -> result.put(row.getCourseId(), row.getTotal()));
        return Map.copyOf(result);
    }

    private Specification<Course> specification(CourseListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.facultyId() != null) predicates.add(cb.equal(root.get("faculty").get("id"), query.facultyId()));
            if (query.departmentId() != null)
                predicates.add(cb.equal(root.get("department").get("id"), query.departmentId()));
            if (query.programId() != null) predicates.add(cb.equal(root.get("program").get("id"), query.programId()));
            if (query.semesterId() != null) predicates.add(cb.equal(root.get("semester").get("id"), query.semesterId()));
            if (query.recommendedAcademicYear() != null)
                predicates.add(cb.equal(root.get("recommendedAcademicYear"), query.recommendedAcademicYear().shortValue()));
            if (query.courseType() != null)
                predicates.add(cb.equal(root.get("courseType"), query.courseType().shortValue()));
            if (query.status() != null)
                predicates.add(query.status().equals("ACTIVE") ? cb.isTrue(root.get("active")) : cb.isFalse(root.get("active")));
            if (query.search() != null) {
                String literal = query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.or(cb.like(cb.lower(root.get("courseCode")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("courseName")), "%" + literal + "%", '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uk_courses_course_code".equals(violation.getConstraintName()))
                return new CourseCodeAlreadyExistsException();
        }
        return exception;
    }

    private Short shortValue(Integer value) { return value == null ? null : value.shortValue(); }
    private void lockTimeout() { entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
    private void positiveId(long id) {
        if (id <= 0) throw new CourseFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
