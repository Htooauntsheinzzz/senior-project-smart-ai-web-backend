package com.smartAiUniversityAssistant.seniorproject.feature.authentication.mapper;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.AppUser;
import java.util.List;
import org.springframework.stereotype.Component;
@Component
public class AuthenticationMapper {
    public CurrentUserResponse profile(AppUser u, List<RoleResponse> roles, boolean restricted) {
        return new CurrentUserResponse(u.getId(), u.getEmployeeId(), u.getFirstName(), u.getLastName(),
                u.getEmail(), u.getPhoneNumber(), u.getDepartmentId(), u.getAccountStatus(), restricted, List.copyOf(roles));
    }
}
