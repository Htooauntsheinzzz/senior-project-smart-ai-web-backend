package com.smartAiUniversityAssistant.seniorproject.feature.program.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProgramRepository extends JpaRepository<Program, Long>, JpaSpecificationExecutor<Program> {
    @EntityGraph(attributePaths = {"department", "department.faculty"})
    Optional<Program> findByIdAndDeletedFalse(Long id);
    @Override @EntityGraph(attributePaths = {"department", "department.faculty"})
    Page<Program> findAll(Specification<Program> specification, Pageable pageable);
    boolean existsByProgramCodeAndIdNot(String code, Long id);
    boolean existsByDepartmentIdAndDeletedFalse(Long departmentId);
    boolean existsByDepartmentIdAndProgramNameAndDegreeLevelAndIdNot(Long departmentId, String name, String degreeLevel, Long id);
    long countByDeletedFalse();
    long countByActiveTrueAndDeletedFalse();
    long countByDepartmentIdAndDeletedFalse(Long departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select p from Program p where p.id = :id")
    Optional<Program> findByIdForUpdate(@Param("id") Long id);

    interface DepartmentCount {
        Long getDepartmentId();
        long getTotal();
    }
    @Query("select p.department.id as departmentId, count(p) as total from Program p where p.deleted = false and p.department.id in :ids group by p.department.id")
    List<DepartmentCount> countForDepartments(@Param("ids") Collection<Long> ids);
}
