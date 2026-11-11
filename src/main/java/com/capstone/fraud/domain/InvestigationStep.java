package com.capstone.fraud.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Ordered, persisted trace of everything an investigation run did:
 * planning, tool calls, retrieval, model calls, validation, decision.
 */
@Getter
@Setter
@Entity
@Table(name = "investigation_step")
public class InvestigationStep {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepType stepType;

    @Column(nullable = false)
    private String name;

    @Column(length = 4000)
    private String inputSummary;

    @Column(length = 8000)
    private String outputSummary;

    /** OK | ERROR | SKIPPED */
    @Column(nullable = false)
    private String status = "OK";

    private long durationMs;

    private int seq;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
