package com.smartAiUniversityAssistant.seniorproject.feature.academicquery;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.AcademicProgressQueryResult;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.mapper.AcademicQueryMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.impl.AcademicProgressQueryServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.impl.AcademicQueryServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.StudentAccountNotActiveException;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.StudentAcademicProgressResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.AcademicProgressService;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import com.smartAiUniversityAssistant.seniorproject.security.CurrentStudentProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** Spec file 56, section 17.1: the progress query service with mocks. */
class AcademicProgressQueryServiceTests {
    private final CurrentStudentProvider currentStudent = mock(CurrentStudentProvider.class);
    private final AcademicProgressService progress = mock(AcademicProgressService.class);
    private final StudentAcademicService academics = mock(StudentAcademicService.class);
    private final AcademicProgressQueryServiceImpl service = new AcademicProgressQueryServiceImpl(currentStudent, progress);
    private final AcademicQueryServiceImpl profileService = new AcademicQueryServiceImpl(currentStudent, academics, null, null, null,
            mock(AcademicQueryMapper.class));

    @Test
    void returnsTheSevenValuesUnchangedForTheTokenStudent() {
        when(currentStudent.getCurrentStudentId()).thenReturn(42L);
        when(progress.getProgress(42L)).thenReturn(new StudentAcademicProgressResponse(new BigDecimal("2.57"), new BigDecimal("18.00"),
                3, 9, 3, 2026, "AVAILABLE"));
        assertThat(service.getMyAcademicProgress()).isEqualTo(new AcademicProgressQueryResult(new BigDecimal("2.57"),
                new BigDecimal("18.00"), 3, 9, 3, 2026, "AVAILABLE"));
        when(progress.getProgress(42L)).thenReturn(new StudentAcademicProgressResponse(null, null, null, null, null, null, "PARTIAL"));
        assertThat(service.getMyAcademicProgress()).isEqualTo(new AcademicProgressQueryResult(null, null, null, null, null, null, "PARTIAL"));
        verify(progress, times(2)).getProgress(42L);
    }

    private static Class<? extends Throwable> thrown(Supplier<?> call) {
        try {
            call.get();
            return null;
        } catch (RuntimeException e) {
            return e.getClass();
        }
    }

    @Test
    void failuresAreTranslatedLikeTheProfileQuery() {
        when(currentStudent.getCurrentStudentId()).thenReturn(42L);
        for (RuntimeException failure : List.of(new DataAccessResourceFailureException("down"), new StudentAccountNotActiveException())) {
            reset(progress, academics);
            when(progress.getProgress(42L)).thenThrow(failure);
            when(academics.getProfile(42L)).thenThrow(failure);
            var fromProgress = thrown(service::getMyAcademicProgress);
            var fromProfile = thrown(profileService::getMyAcademicProfile);
            assertThat(fromProgress).isNotNull().isEqualTo(fromProfile).isEqualTo(failure.getClass());
        }
    }
}
