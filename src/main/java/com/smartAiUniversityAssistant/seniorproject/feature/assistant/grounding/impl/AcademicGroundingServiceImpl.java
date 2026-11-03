package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus.*;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolResult;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.*;
import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Evidence statuses come from the executor's own result: success with an empty list is VERIFIED_EMPTY, the academic
 * query's not-found codes are NOT_FOUND, ACADEMIC_COURSE_AMBIGUOUS is AMBIGUOUS, a database failure
 * (AI_TOOL_EXECUTION_FAILED) is UNAVAILABLE, and an unsupported tool or invalid arguments is ERROR.
 * <p>
 * Decision rules, over every tool call of the request so far: any UNAVAILABLE fails closed (a failed query is never
 * presented as an empty record); otherwise any VERIFIED data lets Gemini phrase the answer, which is then validated;
 * with nothing verified, an ambiguous course is answered with the backend's candidates, a not-found with the matching
 * "not available" reply, and a request whose only results are empty lists with "no active enrollments". ERROR-only
 * results stay with the model so it can correct its arguments within the existing tool limits.
 */
@Service
public class AcademicGroundingServiceImpl implements AcademicGroundingService {
    static final String ACADEMIC_COURSE_AMBIGUOUS = "ACADEMIC_COURSE_AMBIGUOUS";
    static final String COURSE_NOT_FOUND = "COURSE_NOT_FOUND";
    static final String COURSE_SECTION_NOT_FOUND = "COURSE_SECTION_NOT_FOUND";
    static final String STUDENT_COURSE_ENROLLMENT_NOT_FOUND = "STUDENT_COURSE_ENROLLMENT_NOT_FOUND";
    static final String AI_TOOL_EXECUTION_FAILED = "AI_TOOL_EXECUTION_FAILED";
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\p{Zl}\\p{Zp}]");

    private final AcademicGroundingPolicy policy;
    private final SafeAcademicResponseFactory responses;
    private final Clock clock;

    public AcademicGroundingServiceImpl(AcademicGroundingPolicy policy, SafeAcademicResponseFactory responses, Clock clock) {
        this.policy = policy;
        this.responses = responses;
        this.clock = clock;
    }

    @Override
    public AcademicEvidence capture(LlmToolCall call, AcademicAiToolResult result) {
        AcademicEvidenceStatus status;
        Object data = null;
        if (result.success()) {
            data = result.data();
            status = data instanceof Collection<?> rows && rows.isEmpty() ? VERIFIED_EMPTY : VERIFIED;
        } else {
            String code = result.errorCode() == null ? "" : result.errorCode();
            status = switch (code) {
                case ACADEMIC_COURSE_AMBIGUOUS -> AMBIGUOUS;
                case COURSE_NOT_FOUND, COURSE_SECTION_NOT_FOUND, STUDENT_COURSE_ENROLLMENT_NOT_FOUND -> NOT_FOUND;
                case AI_TOOL_EXECUTION_FAILED -> UNAVAILABLE;
                default -> ERROR;
            };
            if (status == AMBIGUOUS) data = result.candidates();
        }
        String identifier = status == ERROR || !policy.toolRequiresGrounding(result.toolName()) ? null : identifier(call);
        return new AcademicEvidence(result.toolName(), status, data, clock.instant(),
                result.success() ? null : result.errorCode(), identifier);
    }

    @Override
    public AcademicGroundingDecision evaluate(String academicIntent, List<AcademicEvidence> evidence) {
        if (evidence == null || evidence.isEmpty())
            return policy.requiresAcademicEvidence(academicIntent)
                    ? AcademicGroundingDecision.blockUnsupported(responses.missingEvidence())
                    : AcademicGroundingDecision.allow();
        if (evidence.stream().anyMatch(e -> e.status() == UNAVAILABLE))
            return AcademicGroundingDecision.verificationFailed(responses.academicServiceUnavailable());
        if (evidence.stream().anyMatch(e -> e.status() == VERIFIED)) return AcademicGroundingDecision.allow();
        Optional<AcademicEvidence> ambiguous = last(evidence, AMBIGUOUS);
        if (ambiguous.isPresent()) {
            List<?> candidates = ambiguous.get().verifiedData() instanceof List<?> list ? list : List.of();
            return AcademicGroundingDecision.clarify(responses.ambiguousCourse(ambiguous.get().requestedIdentifier(), candidates));
        }
        Optional<AcademicEvidence> notFound = last(evidence, NOT_FOUND);
        if (notFound.isPresent()) return AcademicGroundingDecision.notAvailable(notFoundMessage(notFound.get()));
        if (evidence.stream().allMatch(e -> e.status() == VERIFIED_EMPTY))
            return AcademicGroundingDecision.notAvailable(responses.enrollmentUnavailable());
        return AcademicGroundingDecision.allow();
    }

    private String notFoundMessage(AcademicEvidence e) {
        return switch (e.safeErrorCode()) {
            case STUDENT_COURSE_ENROLLMENT_NOT_FOUND -> responses.courseEnrollmentNotFound(e.requestedIdentifier());
            case COURSE_SECTION_NOT_FOUND -> responses.sectionNotFound(e.requestedIdentifier());
            default -> responses.courseNotFound(e.requestedIdentifier());
        };
    }

    private static Optional<AcademicEvidence> last(List<AcademicEvidence> evidence, AcademicEvidenceStatus status) {
        for (int i = evidence.size() - 1; i >= 0; i--) if (evidence.get(i).status() == status) return Optional.of(evidence.get(i));
        return Optional.empty();
    }

    // The identifier the backend queried with; it already passed the executor's and the query service's validation.
    private static String identifier(LlmToolCall call) {
        if (call == null || call.arguments() == null) return null;
        Object value = call.arguments().containsKey("courseIdentifier") ? call.arguments().get("courseIdentifier")
                : call.arguments().get("courseSectionId");
        if (value instanceof Number n) return String.valueOf(n.longValue());
        if (!(value instanceof String s)) return null;
        String clean = CONTROL.matcher(s).replaceAll(" ").strip();
        return clean.isEmpty() ? null : clean.length() > 100 ? clean.substring(0, 100) : clean;
    }
}
