package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.entity.StudentCourseResult;
import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.repository.StudentCourseResultRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.entity.Enrollment;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository.EnrollmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicProgressServiceImpl implements AcademicProgressService {
    private final StudentAcademicService academics;
    private final StudentCourseResultRepository results;
    private final EnrollmentRepository enrollments;

    public AcademicProgressServiceImpl(StudentAcademicService academics, StudentCourseResultRepository results,
            EnrollmentRepository enrollments) {
        this.academics = academics;
        this.results = results;
        this.enrollments = enrollments;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentAcademicProgressResponse getProgress(long studentId) {
        var profile = academics.getProfile(studentId);
        var outcome = AcademicProgressCalculator.calculate(attempts(studentId));
        var summary = summary(studentId, profile);
        BigDecimal cgpa = outcome.cgpa();
        BigDecimal credits = outcome.creditsEarned();
        boolean complete = cgpa != null && credits != null && summary.courseCount() != null && profile.studyYear() != null;
        return new StudentAcademicProgressResponse(cgpa, credits, summary.courseCount(), summary.enrolledCredits(),
                profile.studyYear(), profile.academicYear(), complete ? "AVAILABLE" : "PARTIAL");
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentCurrentSemesterSummaryResponse getCurrentSemesterSummary(long studentId) {
        return summary(studentId, academics.getProfile(studentId));
    }

    // The Student's own semester_id is the only verified "current term": semesters have no dates or current flag.
    private StudentCurrentSemesterSummaryResponse summary(long studentId, StudentAcademicProfileResponse profile) {
        var semester = profile.semester();
        if (semester == null) return new StudentCurrentSemesterSummaryResponse(null, null, null, null);
        List<Enrollment> current = enrollments.findStudentAcademicEnrollments(studentId, EnumSet.of(EnrollmentStatus.ACTIVE))
                .stream().filter(e -> semester.id().equals(e.getCourseSection().getSemester().getId())).toList();
        // Keyed by course, so a Student's meetings or sections can never add a course's credits twice.
        Map<Long, Integer> credits = current.stream().map(e -> e.getCourseSection().getCourse())
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getCreditHours(), (first, second) -> first));
        long lecturers = current.stream().map(e -> e.getCourseSection().getLecture().getId()).distinct().count();
        return new StudentCurrentSemesterSummaryResponse(semester, credits.size(),
                credits.values().stream().mapToInt(Integer::intValue).sum(), Math.toIntExact(lecturers));
    }

    private List<AcademicProgressCalculator.Attempt> attempts(long studentId) {
        List<StudentCourseResult> finalized = new ArrayList<>(results.findFinalizedForProgress(studentId));
        // Oldest to newest: academic year, then the semester's id (semesters have no dates), then the enrollment id.
        finalized.sort(Comparator
                .comparing((StudentCourseResult r) -> r.getEnrollment().getCourseSection().getSemester().getAcademicYear())
                .thenComparing(r -> r.getEnrollment().getCourseSection().getSemester().getId())
                .thenComparing(r -> r.getEnrollment().getId()));
        return IntStream.range(0, finalized.size()).mapToObj(i -> {
            StudentCourseResult r = finalized.get(i);
            var grade = r.getGradeDefinition();
            return new AcademicProgressCalculator.Attempt(r.getEnrollment().getCourseSection().getCourse().getId(), i,
                    grade.getGradePoint(), grade.isCountsInGpa(), grade.isEarnsCredit(), r.getCreditsAttempted(),
                    grade.getGradingScheme().getRepeatAttemptPolicy());
        }).toList();
    }
}
