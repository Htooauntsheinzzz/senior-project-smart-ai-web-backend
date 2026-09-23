package com.smartAiUniversityAssistant.seniorproject.security;
import com.smartAiUniversityAssistant.seniorproject.exception.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ApiErrorWriter writer;
    public RestAuthenticationEntryPoint(ApiErrorWriter writer) { this.writer=writer; }
    @Override public void commence(HttpServletRequest req,HttpServletResponse res,AuthenticationException exception) throws IOException {
        boolean infrastructure=false;
        for (Throwable e=exception;e!=null;e=e.getCause()) if(e instanceof AuthenticationInfrastructureException) infrastructure=true;
        writer.write(req,res,infrastructure?503:401,infrastructure?"AUTH_SERVICE_UNAVAILABLE":"UNAUTHORIZED",
                infrastructure?"Authentication service temporarily unavailable.":"Authentication is required.");
    }
}
