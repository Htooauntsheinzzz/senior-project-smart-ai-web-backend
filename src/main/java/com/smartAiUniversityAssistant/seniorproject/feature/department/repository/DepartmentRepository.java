package com.smartAiUniversityAssistant.seniorproject.feature.department.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, Long>, JpaSpecificationExecutor<Department> {
    @EntityGraph(attributePaths = "faculty")
    Optional<Department> findByIdAndDeletedFalse(Long id);
    @Override @EntityGraph(attributePaths = "faculty")
    Page<Department> findAll(Specification<Department> specification, Pageable pageable);
    boolean existsByDepartmentCodeAndIdNot(String code, Long id);
    boolean existsByFacultyIdAndDepartmentNameAndIdNot(Long facultyId, String name, Long id);
    long countByDeletedFalse();
    long countByActiveTrueAndDeletedFalse();
    long countByFacultyIdAndDeletedFalse(Long facultyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select d from Department d where d.id = :id")
    Optional<Department> findByIdForUpdate(@Param("id") Long id);

    interface FacultyCount {
        Long getFacultyId();
        long getTotal();
    }
    @Query("select d.faculty.id as facultyId, count(d) as total from Department d where d.deleted = false and d.faculty.id in :ids group by d.faculty.id")
    List<FacultyCount> countForFaculties(@Param("ids") Collection<Long> ids);
}
