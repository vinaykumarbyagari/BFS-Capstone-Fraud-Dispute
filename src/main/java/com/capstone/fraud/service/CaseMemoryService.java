package com.capstone.fraud.service;

import com.capstone.fraud.domain.CaseMessage;
import com.capstone.fraud.repo.CaseMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Case-scoped conversation memory. Every message is keyed by caseId, so
 * context is strictly isolated between cases — one case's memory can
 * never be read while working another.
 */
@Service
public class CaseMemoryService {

    private final CaseMessageRepository messages;

    public CaseMemoryService(CaseMessageRepository messages) {
        this.messages = messages;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(String caseId, String role, String content) {
        CaseMessage m = new CaseMessage();
        m.setCaseId(caseId);
        m.setRole(role);
        m.setContent(content.length() > 7900 ? content.substring(0, 7900) : content);
        m.setSeq((int) messages.countByCaseId(caseId) + 1);
        messages.save(m);
    }

    public List<CaseMessage> history(String caseId) {
        return messages.findByCaseIdOrderBySeq(caseId);
    }

    /** Last N turns, oldest-first, for prompt injection. */
    public List<CaseMessage> recent(String caseId, int limit) {
        List<CaseMessage> all = history(caseId);
        return all.size() <= limit ? all : all.subList(all.size() - limit, all.size());
    }
}
