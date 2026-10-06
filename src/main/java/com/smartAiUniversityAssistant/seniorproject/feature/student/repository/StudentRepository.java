package com.smartAiUniversityAssistant.seniorproject.feature.student.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.Student;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {
    @EntityGraph(attributePaths = {"faculty", "department", "program", "semester"})
    Optional<Student> findByIdAndDeletedFalse(Long id);
    @Override @EntityGraph(attributePaths = {"faculty", "department", "program", "semester"})
    Page<Student> findAll(Specification<Student> specification, Pageable pageable);
    Optional<Student> findByUniversityEmailIgnoreCase(String universityEmail);
    boolean existsByStudentCode(String studentCode);
    boolean existsByStudentCodeAndIdNot(String studentCode, Long id);
    boolean existsByUniversityEmailIgnoreCase(String universityEmail);
    boolean existsByUniversityEmailIgnoreCaseAndIdNot(String universityEmail, Long id);
    long countByDeletedFalse();
    long countByAccountStatusAndDeletedFalse(String accountStatus);
    long countByAccountStatusInAndDeletedFalse(Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from Student s where s.studentCode = :studentCode")
    Optional<Student> findByStudentCodeForUpdate(@Param("studentCode") String studentCode);

    // Registration lock: student code plus university email identify the pre-provisioned profile.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from Student s where s.studentCode = :studentCode and lower(s.universityEmail) = lower(:email)")
    Optional<Student> findForRegistration(@Param("studentCode") String studentCode, @Param("email") String email);
}
