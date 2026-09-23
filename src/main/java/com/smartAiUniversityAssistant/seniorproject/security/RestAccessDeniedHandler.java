package com.smartAiUniversityAssistant.seniorproject.security;
import com.smartAiUniversityAssistant.seniorproject.exception.ApiErrorWriter;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private final ApiErrorWriter writer;
    public RestAccessDeniedHandler(ApiErrorWriter writer) { this.writer=writer; }
    @Override public void handle(HttpServletRequest req,HttpServletResponse res,AccessDeniedException e) throws IOException {
        boolean restricted=e instanceof PasswordChangeRequiredException;
        String code=restricted?"PASSWORD_CHANGE_REQUIRED":isAdminUsersPath(req)?"ACCESS_DENIED":"FORBIDDEN";
        writer.write(req,res,403,code,restricted?"Password change is required.":"Access is denied.");
    }
    public static boolean isAdminUsersPath(HttpServletRequest req) {
        String path=req.getRequestURI().substring(req.getContextPath().length());
        return path.equals("/api/v1/admin/users") || path.startsWith("/api/v1/admin/users/");
    }
}
