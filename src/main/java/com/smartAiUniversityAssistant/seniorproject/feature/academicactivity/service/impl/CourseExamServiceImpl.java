package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.entity.CourseExam;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.exception.AcademicActivityFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.mapper.AcademicActivityMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.repository.CourseExamRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.service.CourseExamService;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.CourseSectionNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.CourseSectionRepository;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.time.*;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CourseExamServiceImpl implements CourseExamService {
    private final CourseExamRepository exams;
    private final CourseSectionRepository sections;
    private final AcademicActivityMapper mapper;
    private final Clock clock;

    public CourseExamServiceImpl(CourseExamRepository exams, CourseSectionRepository sections,
            AcademicActivityMapper mapper, Clock clock) {
        this.exams = exams;
        this.sections = sections;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public AcademicActivityPageResponse<CourseExamResponse> list(AuthenticatedUser actor, AcademicActivityListQuery query) {
        requireFull(actor);
        ExamStatus status = null;
        if (query.status() != null) {
            try {
                status = ExamStatus.valueOf(query.status());
            } catch (IllegalArgumentException e) {
                throw invalid("The request is invalid.");
            }
        }
        return AcademicActivityPageResponse.of(exams.search(query.courseSectionId(), status, query.pageable("startsAt"))
                .map(mapper::exam));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CourseExamResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        return mapper.exam(exams.findLive(id).orElseThrow(CourseExamServiceImpl::notFound));
    }

    @Override @Transactional(timeout = 15)
    public CourseExamResponse create(AuthenticatedUser actor, CourseExamRequest request) {
        requireFull(actor);
        var exam = new CourseExam();
        apply(exam, request);
        exam.setStatus(ExamStatus.DRAFT);
        exam.setCreatedBy(actor.userId());
        exam.setCreatedAt(now());
        return mapper.exam(exams.saveAndFlush(exam));
    }

    @Override @Transactional(timeout = 15)
    public CourseExamResponse update(AuthenticatedUser actor, long id, CourseExamRequest request) {
        requireFull(actor);
        CourseExam exam = locked(id);
        if (exam.getStatus() == ExamStatus.CANCELLED || exam.getStatus() == ExamStatus.COMPLETED) throw conflict();
        apply(exam, request);
        touch(exam, actor);
        exams.flush();
        return mapper.exam(exam);
    }

    @Override @Transactional(timeout = 15)
    public CourseExamResponse schedule(AuthenticatedUser actor, long id) {
        return transition(actor, id, ExamStatus.DRAFT, null, ExamStatus.SCHEDULED);
    }

    @Override @Transactional(timeout = 15)
    public CourseExamResponse cancel(AuthenticatedUser actor, long id) {
        return transition(actor, id, ExamStatus.DRAFT, ExamStatus.SCHEDULED, ExamStatus.CANCELLED);
    }

    @Override @Transactional(timeout = 15)
    public CourseExamResponse complete(AuthenticatedUser actor, long id) {
        return transition(actor, id, ExamStatus.SCHEDULED, null, ExamStatus.COMPLETED);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        CourseExam exam = locked(id);
        exam.setDeleted(true);
        touch(exam, actor);
        exams.flush();
    }

    private CourseExamResponse transition(AuthenticatedUser actor, long id, ExamStatus allowed, ExamStatus alsoAllowed, ExamStatus target) {
        requireFull(actor);
        CourseExam exam = locked(id);
        if (exam.getStatus() != allowed && exam.getStatus() != alsoAllowed) throw conflict();
        exam.setStatus(target);
        touch(exam, actor);
        exams.flush();
        return mapper.exam(exam);
    }

    private void apply(CourseExam exam, CourseExamRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) throw invalid("The exam must end after it starts.");
        if (request.roomId() != null) throw new AcademicActivityFailure(400, "ROOM_NOT_FOUND",
                "The referenced room does not exist. Use roomLabel until rooms are managed.");
        ExamType type;
        try {
            type = ExamType.valueOf(request.examType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("The exam type must be QUIZ, MIDTERM, FINAL or PRACTICAL.");
        }
        exam.setCourseSection(sections.findByIdAndDeletedFalse(request.courseSectionId())
                .orElseThrow(CourseSectionNotFoundException::new));
        exam.setTitle(request.title());
        exam.setExamType(type);
        exam.setStartsAt(request.startsAt());
        exam.setEndsAt(request.endsAt());
        exam.setRoomLabel(request.roomLabel());
    }

    private CourseExam locked(long id) {
        return exams.findLiveForUpdate(id).orElseThrow(CourseExamServiceImpl::notFound);
    }
    private void touch(CourseExam exam, AuthenticatedUser actor) {
        exam.setUpdatedBy(actor.userId());
        exam.setUpdatedAt(now());
    }
    private static AcademicActivityFailure invalid(String message) {
        return new AcademicActivityFailure(400, "VALIDATION_ERROR", message);
    }
    private static AcademicActivityFailure notFound() {
        return new AcademicActivityFailure(404, "EXAM_NOT_FOUND", "The requested exam does not exist.");
    }
    private static AcademicActivityFailure conflict() {
        return new AcademicActivityFailure(409, "EXAM_STATUS_CONFLICT", "The exam's status does not allow this change.");
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
}
