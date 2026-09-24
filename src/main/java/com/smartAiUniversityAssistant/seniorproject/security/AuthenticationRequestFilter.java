package com.smartAiUniversityAssistant.seniorproject.security;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.web.filter.OncePerRequestFilter;
import io.micrometer.core.instrument.MeterRegistry;

public class AuthenticationRequestFilter extends OncePerRequestFilter {
    private final AuthenticationThrottle throttle; private final AuthenticationProperties properties;
    private final ApiErrorWriter errors; private final MeterRegistry metrics;
    public AuthenticationRequestFilter(AuthenticationThrottle throttle,AuthenticationProperties properties,ApiErrorWriter errors,MeterRegistry metrics) {
        this.throttle=throttle; this.properties=properties; this.errors=errors; this.metrics=metrics;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        boolean authPath=path.startsWith("/api/v1/admin/auth/");
        boolean usersPath=path.equals("/api/v1/admin/users") || path.startsWith("/api/v1/admin/users/");
        boolean facultiesPath=path.equals("/api/v1/admin/faculties") || path.startsWith("/api/v1/admin/faculties/");
        boolean departmentsPath=path.equals("/api/v1/admin/departments") || path.startsWith("/api/v1/admin/departments/");
        boolean createUser=path.equals("/api/v1/admin/users") && request.getMethod().equals("POST");
        boolean mutationMethod=request.getMethod().equals("POST") || request.getMethod().equals("PUT") || request.getMethod().equals("PATCH");
        boolean adminMutation=(usersPath || facultiesPath || departmentsPath) && mutationMethod;
        long start=System.nanoTime();
        try {
            if (authPath || usersPath || facultiesPath || departmentsPath) response.setHeader("Cache-Control","no-store");
            if (request.getMethod().equals("POST") && (path.equals("/api/v1/admin/auth/login") || path.equals("/api/v1/admin/auth/refresh") || path.equals("/api/v1/admin/auth/change-password")) || adminMutation) {
                if (path.equals("/api/v1/admin/auth/login")) throttle.check("login-ip",request.getRemoteAddr(),properties.throttle().loginPerIp());
                if (path.equals("/api/v1/admin/auth/refresh")) throttle.check("refresh-ip",request.getRemoteAddr(),properties.throttle().refreshPerIp());
                if (createUser) throttle.check("provision-ip",request.getRemoteAddr(),properties.throttle().loginPerIp());
                byte[] bytes=request.getInputStream().readNBytes(16385);
                if ((usersPath || facultiesPath || departmentsPath) && bytes.length>16384) throw new AuthenticationFailure(413,"CONTENT_TOO_LARGE","The request body is too large.");
                var input=new ByteArrayInputStream(bytes);
                request=new HttpServletRequestWrapper(request) {
                    @Override public ServletInputStream getInputStream() {
                        return new ServletInputStream() {
                            public int read() { return input.read(); }
                            public boolean isFinished() { return input.available()==0; }
                            public boolean isReady() { return true; }
                            public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                        };
                    }
                    @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
                };
            }
            chain.doFilter(request,response);
        } catch (AuthenticationFailure e) { errors.write(request,response,e.status(),e.code(),e.getMessage()); }
        catch (AuthenticationInfrastructureException | org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException e) {
            errors.write(request,response,503,"AUTH_SERVICE_UNAVAILABLE","Authentication service temporarily unavailable.");
        } finally {
            if (authPath || usersPath || facultiesPath || departmentsPath) {
                String operation=java.util.Set.of("/api/v1/admin/auth/login","/api/v1/admin/auth/refresh","/api/v1/admin/auth/logout","/api/v1/admin/auth/me","/api/v1/admin/auth/change-password").contains(path)?path:"other";
                metrics.timer("auth.http", "operation",operation,"status",Integer.toString(response.getStatus()))
                        .record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS);
            }
        }
    }
}
