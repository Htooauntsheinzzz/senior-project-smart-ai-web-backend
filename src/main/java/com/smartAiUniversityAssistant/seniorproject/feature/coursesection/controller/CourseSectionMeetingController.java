package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.service.CourseSectionMeetingService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/course-sections/{id:\\d+}/meetings")
public class CourseSectionMeetingController {
    private final CourseSectionMeetingService meetings;
    public CourseSectionMeetingController(CourseSectionMeetingService meetings) { this.meetings = meetings; }

    @GetMapping
    public ResponseEntity<List<CourseSectionMeetingResponse>> list(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(meetings.list(actor, id));
    }
    @PostMapping
    public ResponseEntity<CourseSectionMeetingResponse> create(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @Valid @RequestBody CourseSectionMeetingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(meetings.create(actor, id, request));
    }
    @PutMapping("/{meetingId:\\d+}")
    public ResponseEntity<CourseSectionMeetingResponse> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @PathVariable long meetingId, @Valid @RequestBody CourseSectionMeetingRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(meetings.update(actor, id, meetingId, request));
    }
    @DeleteMapping("/{meetingId:\\d+}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @PathVariable long meetingId) {
        meetings.delete(actor, id, meetingId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
