package com.capstone.fraud.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The authoritative record: a human reviewer's final disposition.
 * The agent's recommendation is advisory only and never dispositions a case.
 */
@Getter
@Setter
@Entity
@Table(name = "review_decision")
public class ReviewDecision {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String reviewerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Disposition disposition;

    @Column(nullable = false, length = 4000)
    private String rationale;

    /** Recommendation the reviewer was shown, for auditability. */
    private String recommendationId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
