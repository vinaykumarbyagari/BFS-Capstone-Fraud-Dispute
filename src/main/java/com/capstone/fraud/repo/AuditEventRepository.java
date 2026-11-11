package com.capstone.fraud.repo;

import com.capstone.fraud.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Deliberately exposes only save/find operations. Audit events are
 * append-only; updates and deletes are also blocked at the entity level.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {
    List<AuditEvent> findByCaseIdOrderByCreatedAt(String caseId);
    List<AuditEvent> findTop100ByOrderByCreatedAtDesc();
}
