package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolNames.*;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception.AcademicCourseAmbiguousException;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception.AcademicQueryFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolResult;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper.AcademicAiToolResultMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolExecutor;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolRegistry;
import com.smartAiUniversityAssistant.seniorproject.feature.course.exception.CourseNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.CourseSectionNotFoundException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Explicit name-to-method mapping onto AcademicQueryService; no reflection and no bean lookup by name. Each
 * AcademicQueryService call is its own short read-only transaction, so nothing stays open during the next LLM call.
 * The Student is resolved inside AcademicQueryService from the SecurityContext; arguments never carry identity.
 */
@Component
public class AcademicAiToolExecutorImpl implements AcademicAiToolExecutor {
    static final String UNSUPPORTED_AI_TOOL = "UNSUPPORTED_AI_TOOL";
    static final String INVALID_AI_TOOL_ARGUMENTS = "INVALID_AI_TOOL_ARGUMENTS";
    static final String AI_TOOL_EXECUTION_FAILED = "AI_TOOL_EXECUTION_FAILED";
    private static final String COURSE_IDENTIFIER = "courseIdentifier";
    private static final String COURSE_SECTION_ID = "courseSectionId";
    private static final Logger log = LoggerFactory.getLogger(AcademicAiToolExecutorImpl.class);

    private final AcademicAiToolRegistry registry;
    private final AcademicQueryService academicQuery;
    private final AcademicAiToolResultMapper mapper;

    public AcademicAiToolExecutorImpl(AcademicAiToolRegistry registry, AcademicQueryService academicQuery,
            AcademicAiToolResultMapper mapper) {
        this.registry = registry;
        this.academicQuery = academicQuery;
        this.mapper = mapper;
    }

    /** Thrown internally when the model's arguments do not match the tool's schema; never leaves this class. */
    private static final class InvalidArguments extends RuntimeException {
        InvalidArguments() { super(null, null, false, false); }
    }

    @Override
    public AcademicAiToolResult execute(LlmToolCall toolCall) {
        String name = toolCall == null ? null : toolCall.name();
        if (!registry.isSupported(name)) {
            // The rejected name comes from the model, so it is not echoed back or logged.
            return AcademicAiToolResult.failure("unsupported", UNSUPPORTED_AI_TOOL, "This tool is not available.");
        }
        Map<String, Object> args = toolCall.arguments();
        try {
            return switch (name) {
                case GET_MY_ACADEMIC_PROFILE -> {
                    noArguments(args);
                    yield one(name, mapper.profile(academicQuery.getMyAcademicProfile()));
                }
                case GET_MY_ENROLLMENTS -> {
                    noArguments(args);
                    yield list(name, academicQuery.getMyEnrollments(), mapper::enrollment);
                }
                case GET_MY_COURSES -> {
                    noArguments(args);
                    yield list(name, academicQuery.getMyCourses(), mapper::course);
                }
                case GET_MY_SCHEDULE -> {
                    noArguments(args);
                    yield list(name, academicQuery.getMySchedule(), mapper::schedule);
                }
                case GET_MY_COURSE_SECTION ->
                        one(name, mapper.courseSection(academicQuery.getMyCourseSection(courseIdentifier(args))));
                case GET_MY_COURSE_LECTURER ->
                        one(name, mapper.lecturer(academicQuery.getMyCourseLecturer(courseIdentifier(args))));
                case GET_COURSE_INFORMATION ->
                        one(name, mapper.courseInformation(academicQuery.getCourseInformation(courseIdentifier(args))));
                case GET_SECTION_AVAILABILITY ->
                        one(name, mapper.sectionAvailability(academicQuery.getSectionAvailability(courseSectionId(args))));
                default -> AcademicAiToolResult.failure("unsupported", UNSUPPORTED_AI_TOOL, "This tool is not available.");
            };
        } catch (InvalidArguments e) {
            return AcademicAiToolResult.failure(name, INVALID_AI_TOOL_ARGUMENTS, "The tool arguments are invalid.");
        } catch (AcademicCourseAmbiguousException e) {
            return AcademicAiToolResult.failure(name, e.code(),
                    "More than one course matches the supplied identifier. Ask the student which course they mean.",
                    mapper.candidates(e.candidates()));
        } catch (AcademicQueryFailure e) {
            // VALIDATION_ERROR here means the identifier passed our checks but not the query service's (e.g. control characters).
            if ("VALIDATION_ERROR".equals(e.code()))
                return AcademicAiToolResult.failure(name, INVALID_AI_TOOL_ARGUMENTS, "The tool arguments are invalid.");
            return AcademicAiToolResult.failure(name, e.code(), e.getMessage());
        } catch (CourseNotFoundException e) {
            return AcademicAiToolResult.failure(name, e.code(), "No course matches the supplied identifier.");
        } catch (CourseSectionNotFoundException e) {
            return AcademicAiToolResult.failure(name, e.code(), "No course section exists with this courseSectionId.");
        } catch (DataAccessException e) {
            log.warn("ASSISTANT_TOOL_FAILURE tool={} error={}", name, e.getClass().getSimpleName());
            return AcademicAiToolResult.failure(name, AI_TOOL_EXECUTION_FAILED, "The academic information could not be retrieved right now.");
        }
    }

    private static AcademicAiToolResult one(String name, Object data) {
        return AcademicAiToolResult.success(name, data, 1);
    }

    private static <T> AcademicAiToolResult list(String name, List<T> rows, Function<T, Map<String, Object>> map) {
        return AcademicAiToolResult.success(name, rows.stream().map(map).toList(), rows.size());
    }

    private static void noArguments(Map<String, Object> args) {
        if (args == null || !args.isEmpty()) throw new InvalidArguments();
    }

    private static String courseIdentifier(Map<String, Object> args) {
        onlyKey(args, COURSE_IDENTIFIER);
        if (!(args.get(COURSE_IDENTIFIER) instanceof String raw)) throw new InvalidArguments();
        String value = raw.strip();
        if (value.isEmpty() || value.length() > AcademicAiToolRegistryImpl.MAX_COURSE_IDENTIFIER_LENGTH) throw new InvalidArguments();
        return value;
    }

    // Whole JSON numbers only (15 or 15.0); strings, fractions, zero, negatives and overflow are rejected.
    private static Long courseSectionId(Map<String, Object> args) {
        onlyKey(args, COURSE_SECTION_ID);
        Object raw = args.get(COURSE_SECTION_ID);
        long value;
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof BigInteger || raw instanceof BigDecimal || raw instanceof Double || raw instanceof Float) {
            try {
                value = new BigDecimal(raw.toString()).longValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw new InvalidArguments();
            }
        } else {
            throw new InvalidArguments();
        }
        if (value <= 0) throw new InvalidArguments();
        return value;
    }

    // Exactly the one expected key: a studentId, role or any other extra field rejects the whole call.
    private static void onlyKey(Map<String, Object> args, String key) {
        if (args == null || args.size() != 1 || !args.containsKey(key)) throw new InvalidArguments();
    }
}
