package com.smartAiUniversityAssistant.seniorproject.feature.timetable.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.AcademicTerm;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long>, JpaSpecificationExecutor<AcademicTerm> {
    @EntityGraph(attributePaths = "semester") Optional<AcademicTerm> findByIdAndDeletedFalse(long id);
    @Override @EntityGraph(attributePaths = "semester") Page<AcademicTerm> findAll(Specification<AcademicTerm> specification, Pageable pageable);
    @EntityGraph(attributePaths = "semester") List<AcademicTerm> findByDeletedFalseOrderByStartDateDescIdDesc();
    boolean existsBySemesterIdAndAcademicYearCodeAndIdNot(long semesterId, String academicYearCode, long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select t from AcademicTerm t where t.id = :id and t.deleted = false")
    Optional<AcademicTerm> findLiveForUpdate(@Param("id") long id);

    @Query("select count(s) > 0 from CourseSection s where s.academicTerm.id = :id and s.deleted = false")
    boolean isReferencedByLiveSections(@Param("id") long id);
}
