package com.capstone.fraud.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "recommendation")
public class Recommendation {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessorMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseRoute route;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    private double confidence;

    @Column(length = 4000)
    private String summary;

    @Column(length = 8000)
    private String reasonsJson;

    @Column(length = 8000)
    private String citationsJson;

    @Column(length = 8000)
    private String evidenceRequestsJson;

    /** True when policy context or model output was insufficient to ground a route. */
    private boolean abstained;

    @Column(length = 2000)
    private String abstainReason;

    /** Raw model output (AI modes) before validation. */
    @Column(length = 16000)
    private String rawOutput;

    private boolean schemaValid;

    private String modelName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
