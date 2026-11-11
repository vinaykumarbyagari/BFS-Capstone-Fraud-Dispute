package com.capstone.fraud.service;

import com.capstone.fraud.domain.AuditEvent;
import com.capstone.fraud.repo.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository auditRepo;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository auditRepo, ObjectMapper objectMapper) {
        this.auditRepo = auditRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String caseId, String actor, String eventType, Object details) {
        AuditEvent e = new AuditEvent();
        e.setCaseId(caseId);
        e.setActor(actor == null ? "system" : actor);
        e.setEventType(eventType);
        e.setDetailsJson(toJson(details));
        auditRepo.save(e);
    }

    private String toJson(Object details) {
        if (details == null) return null;
        if (details instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            return String.valueOf(details);
        }
    }
}
