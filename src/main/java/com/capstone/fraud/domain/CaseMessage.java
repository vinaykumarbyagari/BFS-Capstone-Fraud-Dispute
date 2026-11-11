package com.capstone.fraud.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Case-scoped conversation memory. Messages are strictly partitioned by
 * caseId so context from one case can never leak into another.
 */
@Getter
@Setter
@Entity
@Table(name = "case_message", indexes = @Index(columnList = "caseId"))
public class CaseMessage {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false, length = 8000)
    private String content;

    private int seq;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
