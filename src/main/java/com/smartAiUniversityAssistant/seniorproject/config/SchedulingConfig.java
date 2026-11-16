package com.smartAiUniversityAssistant.seniorproject.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduled tasks. The knowledge ingestion poller is the application's first scheduled task, so Spring Boot's
 * single default scheduler thread is enough; give ingestion its own scheduler if another scheduled task is added.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
