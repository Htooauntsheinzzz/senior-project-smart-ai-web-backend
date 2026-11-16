package com.smartAiUniversityAssistant.seniorproject.feature.timetable.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.AcademicTermService;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.impl.AcademicTermServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/academic-terms")
public class AdminAcademicTermController {
    private final AcademicTermService terms;
    public AdminAcademicTermController(AcademicTermService terms) { this.terms = terms; }

    @GetMapping public ResponseEntity<TimetablePageResponse<AcademicTermResponse>> list(HttpServletRequest request) {
        return ok(terms.list(TimetablePageQuery.from(request, AcademicTermServiceImpl.FILTERS, AcademicTermServiceImpl.SORT, "startDate,desc")));
    }
    @GetMapping("/{id:\\d+}") public ResponseEntity<AcademicTermResponse> detail(@PathVariable long id) { return ok(terms.detail(id)); }
    @PostMapping public ResponseEntity<AcademicTermResponse> create(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody AcademicTermRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(terms.create(actor, body));
    }
    @PutMapping("/{id:\\d+}") public ResponseEntity<AcademicTermResponse> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @Valid @RequestBody AcademicTermRequest body) {
        return ok(terms.update(actor, id, body));
    }
    @PatchMapping("/{id:\\d+}/status") public ResponseEntity<AcademicTermResponse> status(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @Valid @RequestBody AcademicTermStatusRequest body) {
        return ok(terms.status(actor, id, body));
    }
    @DeleteMapping("/{id:\\d+}") public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        terms.delete(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private static <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
