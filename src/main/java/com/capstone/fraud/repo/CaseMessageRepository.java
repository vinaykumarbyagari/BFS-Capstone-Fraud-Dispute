package com.capstone.fraud.repo;

import com.capstone.fraud.domain.CaseMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseMessageRepository extends JpaRepository<CaseMessage, String> {
    List<CaseMessage> findByCaseIdOrderBySeq(String caseId);
    long countByCaseId(String caseId);
}
