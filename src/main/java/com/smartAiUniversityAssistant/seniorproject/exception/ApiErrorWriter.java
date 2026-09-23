package com.smartAiUniversityAssistant.seniorproject.exception;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiErrorWriter {
    private final ObjectMapper mapper;
    private final Clock clock;
    public ApiErrorWriter(ObjectMapper mapper, Clock clock) { this.mapper=mapper; this.clock=clock; }
    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message) throws IOException {
        if (response.isCommitted()) return;
        String trace = (String) request.getAttribute("auth.traceId");
        if (trace == null) { trace = UUID.randomUUID().toString(); request.setAttribute("auth.traceId",trace); }
        response.setStatus(status); response.setContentType("application/json");
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Trace-Id",trace);
        if (status==401 && !(request.getMethod().equals("POST") && (request.getRequestURI().equals(request.getContextPath()+"/api/v1/admin/auth/login")
                || request.getRequestURI().equals(request.getContextPath()+"/api/v1/admin/auth/refresh")))) response.setHeader("WWW-Authenticate","Bearer");
        if (status==429) response.setHeader("Retry-After","60");
        if (status>=500) org.slf4j.LoggerFactory.getLogger(ApiErrorWriter.class).error("{} traceId={}",code,trace);
        mapper.writeValue(response.getOutputStream(),new ApiError(clock.instant(),status,code,message,request.getRequestURI(),trace));
    }
}
