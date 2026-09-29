package com.smartAiUniversityAssistant.seniorproject.feature.lecture.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.department.exception.DepartmentNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.department.exception.InvalidFacultyException;
import com.smartAiUniversityAssistant.seniorproject.feature.department.repository.DepartmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.exception.FacultyNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.repository.FacultyRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.entity.Lecture;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.mapper.LectureMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.repository.LectureRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.service.LectureService;
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
public class LectureServiceImpl implements LectureService {
    private final LectureRepository lectures;
    private final FacultyRepository faculties;
    private final DepartmentRepository departments;
    private final LectureMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public LectureServiceImpl(LectureRepository lectures, FacultyRepository faculties,
            DepartmentRepository departments, LectureMapper mapper, Clock clock, EntityManager entityManager) {
        this.lectures = lectures;
        this.faculties = faculties;
        this.departments = departments;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(timeout = 15)
    public LectureResponse create(AuthenticatedUser actor, LectureCreateRequest request) {
        requireFull(actor);
        lockTimeout();
        duplicates(0L, request.departmentId(), request.lectureNameTh(), request.lectureNameEn());
        Department department = selectedDepartment(request.departmentId());
        Faculty faculty = selectedFaculty(request.facultyId());
        requireFacultyMatch(department, faculty);
        var lecture = new Lecture();
        lecture.setLectureNameTh(request.lectureNameTh());
        lecture.setLectureNameEn(request.lectureNameEn());
        lecture.setLectureNickname(request.lectureNickname());
        lecture.setFaculty(faculty);
        lecture.setDepartment(department);
        lecture.setStatus(request.status());
        lecture.setDeleted(false);
        lecture.setCreatedBy(actor.userId());
        lecture.setCreatedAt(now());
        try {
            return mapper.response(lectures.saveAndFlush(lecture));
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public LecturePageResponse list(AuthenticatedUser actor, LectureListQuery query) {
        requireFull(actor);
        return mapper.page(lectures.findAll(specification(query), query.pageable()), query);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public LectureResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        return mapper.response(lectures.findByIdAndDeletedFalse(id).orElseThrow(LectureNotFoundException::new));
    }

    @Override @Transactional(timeout = 15)
    public LectureResponse update(AuthenticatedUser actor, long id, LectureUpdateRequest request) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Lecture lecture = locked(id);
        duplicates(id, request.departmentId(), request.lectureNameTh(), request.lectureNameEn());
        Department department = selectedDepartment(request.departmentId());
        Faculty faculty = selectedFaculty(request.facultyId());
        requireFacultyMatch(department, faculty);
        lecture.setLectureNameTh(request.lectureNameTh());
        lecture.setLectureNameEn(request.lectureNameEn());
        lecture.setLectureNickname(request.lectureNickname());
        lecture.setFaculty(faculty);
        lecture.setDepartment(department);
        lecture.setStatus(request.status());
        lecture.setUpdatedBy(actor.userId());
        lecture.setUpdatedAt(now());
        try {
            lectures.flush();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return mapper.response(lecture);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Lecture lecture = locked(id);
        lecture.setDeleted(true);
        lecture.setUpdatedBy(actor.userId());
        lecture.setUpdatedAt(now());
        lectures.flush();
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public LectureSummaryResponse summary(AuthenticatedUser actor) {
        requireFull(actor);
        long total = lectures.countByDeletedFalse();
        long active = lectures.countByStatusAndDeletedFalse("ACTIVE");
        long inactive = lectures.countByStatusAndDeletedFalse("INACTIVE");
        return new LectureSummaryResponse(total, active, inactive);
    }

    private Lecture locked(long id) {
        Lecture lecture = lectures.findByIdForUpdate(id).orElseThrow(LectureNotFoundException::new);
        if (lecture.isDeleted()) throw new LectureNotFoundException();
        return lecture;
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
        if (!department.isActive()) throw new InvalidLectureDepartmentException();
        return department;
    }

    private void requireFacultyMatch(Department department, Faculty faculty) {
        if (!department.getFaculty().getId().equals(faculty.getId())) throw new InvalidLectureDepartmentException();
    }

    private void duplicates(long id, Long departmentId, String nameTh, String nameEn) {
        // The scoped UNIQUE constraint reserves name combinations even after soft deletion.
        if (lectures.existsByDepartmentIdAndLectureNameThAndLectureNameEnAndIdNot(departmentId, nameTh, nameEn, id))
            throw new LectureAlreadyExistsException();
    }

    private Specification<Lecture> specification(LectureListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.facultyId() != null) predicates.add(cb.equal(root.get("faculty").get("id"), query.facultyId()));
            if (query.departmentId() != null)
                predicates.add(cb.equal(root.get("department").get("id"), query.departmentId()));
            if (query.status() != null) predicates.add(cb.equal(root.get("status"), query.status()));
            if (query.search() != null) {
                String literal = query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.or(cb.like(cb.lower(root.get("lectureNameTh")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("lectureNameEn")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("lectureNickname")), "%" + literal + "%", '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uk_lectures_department_names".equals(violation.getConstraintName()))
                return new LectureAlreadyExistsException();
        }
        return exception;
    }

    private void lockTimeout() { entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
    private void positiveId(long id) {
        if (id <= 0) throw new LectureFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
