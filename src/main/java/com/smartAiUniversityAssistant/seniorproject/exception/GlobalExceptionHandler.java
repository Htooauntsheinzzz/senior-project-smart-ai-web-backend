package com.smartAiUniversityAssistant.seniorproject.exception;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AdminAccountProvisioningFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AdminAccountManagementFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.user.exception.AdminUserQueryValidationException;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.exception.*;
import com.smartAiUniversityAssistant.seniorproject.security.RestAccessDeniedHandler;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.TransactionException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final ApiErrorWriter writer;
    public GlobalExceptionHandler(ApiErrorWriter writer) { this.writer=writer; }
    @ExceptionHandler(com.smartAiUniversityAssistant.seniorproject.feature.department.exception.DepartmentFailure.class)
    void department(com.smartAiUniversityAssistant.seniorproject.feature.department.exception.DepartmentFailure e,
            HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,e.status(),e.code(),e.getMessage());
    }
    @ExceptionHandler(com.smartAiUniversityAssistant.seniorproject.feature.program.exception.ProgramFailure.class)
    void program(com.smartAiUniversityAssistant.seniorproject.feature.program.exception.ProgramFailure e,
            HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,e.status(),e.code(),e.getMessage());
    }
    @ExceptionHandler(AuthenticationFailure.class)
    void authentication(AuthenticationFailure e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,e.status(),e.code(),e.getMessage());
    }
    @ExceptionHandler(AdminAccountProvisioningFailure.class)
    void provisioning(AdminAccountProvisioningFailure e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,e.status(),e.code(),e.getMessage());
    }
    @ExceptionHandler(AdminAccountManagementFailure.class)
    void management(AdminAccountManagementFailure e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,e.status(),e.code(),e.getMessage());
    }
    @ExceptionHandler(AdminUserQueryValidationException.class)
    void queryValidation(AdminUserQueryValidationException e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,400,"VALIDATION_ERROR","The request is invalid.");
    }
    @ExceptionHandler(FacultyQueryValidationException.class)
    void facultyQueryValidation(FacultyQueryValidationException e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,400,"VALIDATION_ERROR","The request is invalid.");
    }
    @ExceptionHandler(FacultyNotFoundException.class)
    void facultyNotFound(FacultyNotFoundException e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,404,"FACULTY_NOT_FOUND",e.getMessage());
    }
    @ExceptionHandler(FacultyCodeAlreadyExistsException.class)
    void facultyCodeDuplicate(FacultyCodeAlreadyExistsException e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,409,"FACULTY_CODE_ALREADY_EXISTS",e.getMessage());
    }
    @ExceptionHandler(FacultyNameAlreadyExistsException.class)
    void facultyNameDuplicate(FacultyNameAlreadyExistsException e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,409,"FACULTY_NAME_ALREADY_EXISTS",e.getMessage());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,jakarta.validation.ConstraintViolationException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    void validation(Exception e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,400,"VALIDATION_ERROR","The request is invalid.");
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    void methodNotSupported(Exception e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,405,"METHOD_NOT_ALLOWED","The request method is not supported.");
    }
    @ExceptionHandler(PessimisticLockingFailureException.class)
    void concurrencyConflict(Exception e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,409,"CONCURRENT_MODIFICATION","The record was modified concurrently. Retry the request.");
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    void unsupportedMedia(Exception e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,415,"UNSUPPORTED_MEDIA_TYPE","Content-Type application/json is required.");
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    void unacceptableMedia(Exception e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,406,"NOT_ACCEPTABLE","The requested response media type is not supported.");
    }
    @ExceptionHandler({DataAccessException.class,TransactionException.class,AuthenticationInfrastructureException.class})
    void infrastructure(Exception e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,503,"AUTH_SERVICE_UNAVAILABLE","Authentication service temporarily unavailable.");
    }
    @ExceptionHandler(AccessDeniedException.class)
    void forbidden(AccessDeniedException e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        boolean restricted=e instanceof com.smartAiUniversityAssistant.seniorproject.security.PasswordChangeRequiredException;
        String code=restricted?"PASSWORD_CHANGE_REQUIRED":RestAccessDeniedHandler.isAdminUsersPath(req)?"ACCESS_DENIED":"FORBIDDEN";
        writer.write(req,res,403,code,restricted?"Password change is required.":"Access is denied.");
    }
    @ExceptionHandler(Exception.class)
    void unexpected(Exception e,HttpServletRequest req,HttpServletResponse res) throws IOException {
        writer.write(req,res,500,"INTERNAL_ERROR","Unable to complete the request.");
    }
}
