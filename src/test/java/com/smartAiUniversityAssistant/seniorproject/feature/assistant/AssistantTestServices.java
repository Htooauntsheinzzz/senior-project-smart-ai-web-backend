package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolExecutor;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.ConversationContextService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolRegistryImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicReferenceResolverImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.StudentAssistantServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import com.smartAiUniversityAssistant.seniorproject.security.CurrentStudentProvider;
import java.time.Clock;

/** The chat service with its real registry, reference resolver and Step 11 grounding layer around mocked collaborators. */
public final class AssistantTestServices {
    private AssistantTestServices() {
    }

    public static StudentAssistantServiceImpl service(CurrentStudentProvider currentStudent, StudentAcademicService academics,
            LlmClient llm, AcademicAiToolExecutor toolExecutor, AiAssistantProperties properties,
            ConversationContextService conversations, Clock clock) {
        var policy = new AcademicGroundingPolicyImpl();
        var responses = new SafeAcademicResponseFactoryImpl();
        return new StudentAssistantServiceImpl(currentStudent, academics, llm, new AcademicAiToolRegistryImpl(), toolExecutor,
                properties, conversations, new AcademicReferenceResolverImpl(), policy,
                new AcademicGroundingServiceImpl(policy, responses, clock), new GroundedAnswerValidatorImpl(), responses, clock);
    }
}
