package com.capstone.fraud.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A chunk of an ingested policy document. The citation metadata
 * (policyId/version/section) travels with the chunk so retrieved
 * passages can be cited exactly.
 */
@Getter
@Setter
@Entity
@Table(name = "policy_chunk")
public class PolicyChunk {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String policyId;

    @Column(nullable = false)
    private String policyVersion;

    @Column(nullable = false)
    private String policyTitle;

    private String section;

    private int chunkIndex;

    @Column(nullable = false, length = 8000)
    private String text;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
