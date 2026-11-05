package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.enums.RepeatAttemptPolicy;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "grading_schemes") @Getter @Setter
public class GradingScheme {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "scheme_code", nullable = false, length = 50) private String schemeCode;
    @Column(name = "scheme_name", nullable = false, length = 150) private String schemeName;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_attempt_policy", length = 30) private RepeatAttemptPolicy repeatAttemptPolicy;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
