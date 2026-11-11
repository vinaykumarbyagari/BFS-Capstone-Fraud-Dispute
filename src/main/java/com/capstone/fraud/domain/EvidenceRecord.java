package com.capstone.fraud.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One persisted observation of a single evidence-tool invocation.
 * Together with {@link InvestigationStep} this forms the observable
 * execution trace for a run.
 */
@Getter
@Setter
@Entity
@Table(name = "evidence_record")
public class EvidenceRecord {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String runId;

    @Column(nullable = false)
    private String toolName;

    @Column(length = 4000)
    private String argumentsJson;

    /** SUCCESS | NOT_FOUND | ERROR | REJECTED */
    @Column(nullable = false)
    private String outcome;

    @Column(length = 8000)
    private String outputJson;

    private long durationMs;

    private int seq;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
