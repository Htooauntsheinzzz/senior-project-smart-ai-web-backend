package com.smartAiUniversityAssistant.seniorproject.feature.user.dto.request;

import com.smartAiUniversityAssistant.seniorproject.feature.user.validation.RequiredKeysPresent;
import jakarta.validation.constraints.*;
import java.util.HashSet;
import java.util.Set;

@RequiredKeysPresent
public final class UpdateAdminUserRequest {
    @NotBlank @Size(max = 50) private String employeeId;
    @Size(max = 30) @Pattern(regexp = "^(?=.*[0-9])[0-9 +()\\-]+$") private String phoneNumber;
    @NotBlank @Size(max = 100) private String firstName;
    @NotBlank @Size(max = 100) private String lastName;
    @NotBlank @Size(max = 255) @Email private String email;
    @Positive private Long departmentId;
    @NotNull @Pattern(regexp = "ACTIVE|INACTIVE|LOCKED|SUSPENDED") private String accountStatus;
    @NotNull @Positive private Long roleId;
    private final Set<String> presentKeys = new HashSet<>();

    public UpdateAdminUserRequest() {}

    public String employeeId() { return employeeId; }
    public String phoneNumber() { return phoneNumber; }
    public String firstName() { return firstName; }
    public String lastName() { return lastName; }
    public String email() { return email; }
    public Long departmentId() { return departmentId; }
    public String accountStatus() { return accountStatus; }
    public Long roleId() { return roleId; }
    public Set<String> presentKeys() { return presentKeys; }

    public void setEmployeeId(String value) { presentKeys.add("employeeId"); employeeId = trim(value); }
    public void setPhoneNumber(String value) {
        presentKeys.add("phoneNumber");
        String trimmed = trim(value);
        phoneNumber = trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
    public void setFirstName(String value) { presentKeys.add("firstName"); firstName = trim(value); }
    public void setLastName(String value) { presentKeys.add("lastName"); lastName = trim(value); }
    public void setEmail(String value) { presentKeys.add("email"); email = trim(value); }
    public void setDepartmentId(Long value) { presentKeys.add("departmentId"); departmentId = value; }
    public void setAccountStatus(String value) { presentKeys.add("accountStatus"); accountStatus = value; }
    public void setRoleId(Long value) { presentKeys.add("roleId"); roleId = value; }

    private static String trim(String value) { return value == null ? null : value.trim(); }

    @Override public String toString() { return "UpdateAdminUserRequest[REDACTED]"; }
}
