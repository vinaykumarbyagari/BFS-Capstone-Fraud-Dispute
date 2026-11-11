package com.capstone.fraud.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail. Updates and deletes are blocked at the
 * entity level; the trail can only grow.
 */
@Getter
@Setter
@Entity
@Table(name = "audit_event", indexes = @Index(columnList = "caseId"))
public class AuditEvent {

    @Id
    private String id = UUID.randomUUID().toString();

    private String caseId;

    /** Principal who caused the event (user, reviewer or "system"). */
    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String eventType;

    @Column(length = 8000)
    private String detailsJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PreUpdate
    void blockUpdate() {
        throw new IllegalStateException("Audit events are append-only and cannot be modified");
    }

    @PreRemove
    void blockDelete() {
        throw new IllegalStateException("Audit events are append-only and cannot be deleted");
    }
}
