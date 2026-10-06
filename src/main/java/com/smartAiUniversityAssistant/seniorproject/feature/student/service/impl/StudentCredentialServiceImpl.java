package com.smartAiUniversityAssistant.seniorproject.feature.student.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.PasswordPolicy;
import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.enums.StudentAccountStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.time.*;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class StudentCredentialServiceImpl implements StudentCredentialService {
    private final StudentRepository students;
    private final StudentCredentialRepository credentials;
    private final PasswordPolicy policy;
    private final PasswordEncoder encoder;
    private final AuthenticationProperties properties;
    private final StudentCacheService cache;
    private final Clock clock;

    public StudentCredentialServiceImpl(StudentRepository students, StudentCredentialRepository credentials,
            PasswordPolicy policy, PasswordEncoder encoder, AuthenticationProperties properties,
            StudentCacheService cache, Clock clock) {
        this.students = students;
        this.credentials = credentials;
        this.policy = policy;
        this.encoder = encoder;
        this.properties = properties;
        this.cache = cache;
        this.clock = clock;
    }

    @Override
    public String encodeNewPassword(String password, String confirmPassword) {
        if (!Objects.equals(password, confirmPassword)) throw new PasswordConfirmationMismatchException();
        policy.validateNew(password, null);
        return encoder.encode(password);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public StudentCredential create(Student student, String passwordHash, boolean forcePasswordChange) {
        LocalDateTime now = now();
        var credential = new StudentCredential();
        credential.setStudent(student);
        credential.setPasswordHash(passwordHash);
        credential.setForcePasswordChange(forcePasswordChange);
        credential.setPasswordChangedAt(forcePasswordChange ? null : now);
        credential.setFailedLoginAttempts(0);
        credential.setCreatedAt(now);
        try {
            return credentials.saveAndFlush(credential);
        } catch (DataIntegrityViolationException e) {
            throw StudentConstraints.translate(e);
        }
    }

    @Override @Transactional(timeout = 15)
    public void resetPassword(AuthenticatedUser actor, long studentId, StudentPasswordResetRequest request) {
        String hash = encodeNewPassword(request.temporaryPassword(), request.confirmPassword());
        Student student = students.findByIdForUpdate(studentId).filter(s -> !s.isDeleted())
                .orElseThrow(StudentNotFoundException::new);
        boolean pending = StudentAccountStatus.PENDING.name().equals(student.getAccountStatus());
        StudentCredential credential = credentials.findByStudentIdForUpdate(studentId).orElse(null);
        if (credential == null) throw pending ? new StudentNotRegisteredException() : new InvalidStudentAccountStateException();
        if (pending) throw new InvalidStudentAccountStateException();
        LocalDateTime now = now();
        // Reset never touches account_status: an INACTIVE or SUSPENDED student stays disabled.
        credential.setPasswordHash(hash);
        credential.setForcePasswordChange(true);
        credential.setPasswordChangedAt(now);
        credential.setFailedLoginAttempts(0);
        credential.setLockedUntil(null);
        credential.setUpdatedAt(now);
        student.setUpdatedBy(actor.userId());
        student.setUpdatedAt(now);
        credentials.flush();
        cache.revokeAfterCommit(studentId);
    }

    @Override @Transactional(timeout = 15)
    public Student completeRegistration(StudentRegisterRequest request, String passwordHash) {
        Student student = students.findForRegistration(request.studentCode(), request.universityEmail()).orElse(null);
        if (student == null) return registerNewStudent(request, passwordHash);
        if (student.isDeleted()) throw new StudentRegistrationNotAllowedException();
        boolean hasCredential = credentials.existsByStudentId(student.getId());
        switch (StudentAccountStatus.parse(student.getAccountStatus())) {
            case PENDING -> { if (hasCredential) throw new InvalidStudentAccountStateException(); }
            case ACTIVE -> throw hasCredential ? new StudentAlreadyRegisteredException()
                    : new InvalidStudentAccountStateException();
            case INACTIVE, SUSPENDED -> throw new StudentRegistrationNotAllowedException();
        }
        // Identity: when the profile holds a date of birth, the registrant must supply the same one.
        if (student.getDateOfBirth() != null && !student.getDateOfBirth().equals(request.dateOfBirth()))
            throw new StudentNotFoundException();
        create(student, passwordHash, false);
        student.setAccountStatus(StudentAccountStatus.ACTIVE.name());
        student.setUpdatedAt(now());
        students.flush();
        cache.evictAfterCommit(student.getId());
        return student;
    }

    /**
     * Open self-registration: a student not yet in the system creates an ACTIVE account directly.
     * Faculty, department and program stay empty until the student sets them on their profile, and no
     * password change is forced because the student chose the password.
     */
    private Student registerNewStudent(StudentRegisterRequest request, String passwordHash) {
        if (request.firstName() == null || request.lastName() == null)
            throw new StudentFailure(400, "VALIDATION_ERROR", "firstName and lastName are required for a new student.");
        if (students.existsByStudentCode(request.studentCode())) throw new StudentCodeAlreadyExistsException();
        if (students.existsByUniversityEmailIgnoreCase(request.universityEmail()))
            throw new StudentEmailAlreadyExistsException();
        var student = new Student();
        student.setStudentCode(request.studentCode());
        student.setUniversityEmail(request.universityEmail());
        student.setFirstName(request.firstName());
        student.setLastName(request.lastName());
        student.setPhoneNumber(request.phoneNumber());
        student.setDateOfBirth(request.dateOfBirth());
        student.setAccountStatus(StudentAccountStatus.ACTIVE.name());
        student.setDeleted(false);
        student.setCreatedAt(now());
        try {
            students.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            throw StudentConstraints.translate(e);
        }
        create(student, passwordHash, false);
        return student;
    }

    @Override @Transactional(timeout = 15, noRollbackFor = StudentFailure.class)
    public LoginOutcome verifyLogin(String universityEmail, String password) {
        Student student = students.findByUniversityEmailIgnoreCase(universityEmail).orElse(null);
        if (student == null || student.isDeleted()) {
            policy.dummyMatch(password);
            throw new InvalidStudentCredentialsException();
        }
        if (!StudentAccountStatus.ACTIVE.name().equals(student.getAccountStatus()))
            throw new StudentAccountNotActiveException();
        StudentCredential credential = credentials.findByStudentIdForUpdate(student.getId())
                .orElseThrow(InvalidStudentAccountStateException::new);
        LocalDateTime now = now();
        if (credential.getLockedUntil() != null) {
            if (credential.getLockedUntil().isAfter(now)) throw new StudentAccountLockedException();
            credential.setLockedUntil(null);
            credential.setFailedLoginAttempts(0);
        }
        if (!policy.matches(password, credential.getPasswordHash())) {
            // Only the credential row changes; account_status is never touched by failed logins.
            int attempts = Math.min(properties.lockout().maxFailedAttempts(), credential.getFailedLoginAttempts() + 1);
            credential.setFailedLoginAttempts(attempts);
            if (attempts >= properties.lockout().maxFailedAttempts())
                credential.setLockedUntil(now.plus(properties.lockout().duration()));
            credential.setUpdatedAt(now);
            credentials.flush();
            throw new InvalidStudentCredentialsException();
        }
        if (credential.getFailedLoginAttempts() != 0 || credential.getLockedUntil() != null) {
            credential.setFailedLoginAttempts(0);
            credential.setLockedUntil(null);
        }
        credential.setUpdatedAt(now);
        cache.evictAfterCommit(student.getId());
        return new LoginOutcome(student.getId(), student.getStudentCode(), credential.isForcePasswordChange());
    }

    @Override @Transactional(timeout = 15)
    public void changePassword(long studentId, StudentChangePasswordRequest request) {
        if (!policy.validInput(request.currentPassword())) throw new CurrentPasswordIncorrectException();
        Student student = students.findByIdForUpdate(studentId).orElseThrow(StudentAccountNotActiveException::new);
        if (student.isDeleted() || !StudentAccountStatus.ACTIVE.name().equals(student.getAccountStatus()))
            throw new StudentAccountNotActiveException();
        StudentCredential credential = credentials.findByStudentIdForUpdate(studentId)
                .orElseThrow(InvalidStudentAccountStateException::new);
        if (!policy.matches(request.currentPassword(), credential.getPasswordHash()))
            throw new CurrentPasswordIncorrectException();
        policy.validateNew(request.newPassword(), credential.getPasswordHash());
        LocalDateTime now = now();
        credential.setPasswordHash(encoder.encode(request.newPassword()));
        credential.setForcePasswordChange(false);
        credential.setPasswordChangedAt(now);
        credential.setFailedLoginAttempts(0);
        credential.setLockedUntil(null);
        credential.setUpdatedAt(now);
        credentials.flush();
        cache.revokeAfterCommit(studentId);
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
