package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.AcademicProgressQueryResult;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception.AcademicQueryFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicProgressQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.course.exception.CourseFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.CourseSectionFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception.EnrollmentFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.StudentFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.AcademicProgressService;
import com.smartAiUniversityAssistant.seniorproject.security.CurrentStudentProvider;
import com.smartAiUniversityAssistant.seniorproject.security.PasswordChangeRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 16A (spec file 56, section 6): AcademicProgressService.getProgress for the Student of the SecurityContext, with
 * the logging and exception behaviour of AcademicQueryServiceImpl.query for get_my_academic_profile (the outcome is
 * logged, the exception is rethrown unchanged). Nothing is written, rounded, defaulted or recomputed; no progress value
 * is logged.
 */
@Service
public class AcademicProgressQueryServiceImpl implements AcademicProgressQueryService {
    private static final Logger log = LoggerFactory.getLogger(AcademicProgressQueryServiceImpl.class);
    static final String TOOL = "get_my_academic_progress";

    private final CurrentStudentProvider currentStudent;
    private final AcademicProgressService progress;

    public AcademicProgressQueryServiceImpl(CurrentStudentProvider currentStudent, AcademicProgressService progress) {
        this.currentStudent = currentStudent;
        this.progress = progress;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public AcademicProgressQueryResult getMyAcademicProgress() {
        long started = System.nanoTime();
        try {
            var p = progress.getProgress(currentStudent.getCurrentStudentId());
            var result = new AcademicProgressQueryResult(p.cgpa(), p.creditsEarned(), p.currentCourses(), p.currentEnrolledCredits(),
                    p.studyYear(), p.academicYear(), p.dataStatus());
            log.info("ACADEMIC_QUERY tool={} argument={} success=true resultCount={} executionTimeMs={}", TOOL, "-", 1, elapsedMs(started));
            return result;
        } catch (RuntimeException e) {
            log.info("ACADEMIC_QUERY tool={} argument={} success=false errorCode={} executionTimeMs={}", TOOL, "-", errorCode(e),
                    elapsedMs(started));
            throw e;
        }
    }

    private static long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }

    // The same codes as AcademicQueryServiceImpl.errorCode.
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
