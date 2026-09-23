package com.smartAiUniversityAssistant.seniorproject.feature.user.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.smartAiUniversityAssistant.seniorproject.feature.user.validation.*;
import jakarta.validation.constraints.*;

@PasswordsMatch
public final class CreateAdminUserRequest {
    @NotBlank @Size(max = 50) private String employeeId;
    @Size(max = 30) @Pattern(regexp = "^(?=.*[0-9])[0-9 +()\\-]+$") private String phoneNumber;
    @NotBlank @Size(max = 100) private String firstName;
    @NotBlank @Size(max = 100) private String lastName;
    @NotBlank @Size(max = 255) @Email private String email;
    @Positive private Long departmentId;
    @NotNull @Pattern(regexp = "ACTIVE|INACTIVE|LOCKED|SUSPENDED") private String accountStatus = "ACTIVE";
    @NotNull @Positive private Long roleId;
    @NotNull @ValidPassword(requireMinimum = true) private String temporaryPassword;
    @NotNull @ValidPassword private String confirmPassword;
    @NotNull private Boolean forcePasswordChange = true;

    public CreateAdminUserRequest() {}

    public String employeeId() { return employeeId; }
    public String phoneNumber() { return phoneNumber; }
    public String firstName() { return firstName; }
    public String lastName() { return lastName; }
    public String email() { return email; }
    public Long departmentId() { return departmentId; }
    public String accountStatus() { return accountStatus; }
    public Long roleId() { return roleId; }
    public String temporaryPassword() { return temporaryPassword; }
    public String confirmPassword() { return confirmPassword; }
    public boolean forcePasswordChange() { return forcePasswordChange; }

    public void setEmployeeId(String value) { employeeId = trim(value); }
    public void setPhoneNumber(String value) { phoneNumber = blankToNull(value); }
    public void setFirstName(String value) { firstName = trim(value); }
    public void setLastName(String value) { lastName = trim(value); }
    public void setEmail(String value) { email = trim(value); }
    public void setDepartmentId(Long value) { departmentId = value; }
    @JsonSetter(value = "accountStatus", nulls = Nulls.FAIL)
    public void setAccountStatus(String value) { accountStatus = value; }
    public void setRoleId(Long value) { roleId = value; }
    public void setTemporaryPassword(String value) { temporaryPassword = value; }
    public void setConfirmPassword(String value) { confirmPassword = value; }
    @JsonSetter(value = "forcePasswordChange", nulls = Nulls.FAIL)
    public void setForcePasswordChange(Boolean value) { forcePasswordChange = value; }

    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String blankToNull(String value) {
        value = trim(value);
        return value == null || value.isEmpty() ? null : value;
    }

    @Override public String toString() { return "CreateAdminUserRequest[REDACTED]"; }
}
