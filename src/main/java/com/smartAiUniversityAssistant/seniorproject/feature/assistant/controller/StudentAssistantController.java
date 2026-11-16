package com.smartAiUniversityAssistant.seniorproject.feature.assistant.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.StudentAssistantChatRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.StudentAssistantChatResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.StudentAssistantService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

// Route security (student token, ROLE_STUDENT, no forced password change) comes from the /api/v1/student/** rule.
@RestController
@RequestMapping("/api/v1/student/assistant")
public class StudentAssistantController {
    private final StudentAssistantService assistant;
    public StudentAssistantController(StudentAssistantService assistant) { this.assistant = assistant; }

    @PostMapping("/chat")
    public ResponseEntity<StudentAssistantChatResponse> chat(@Valid @RequestBody StudentAssistantChatRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(assistant.chat(request));
    }
}
