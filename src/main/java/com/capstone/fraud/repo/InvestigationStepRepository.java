package com.capstone.fraud.repo;

import com.capstone.fraud.domain.InvestigationStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestigationStepRepository extends JpaRepository<InvestigationStep, String> {
    List<InvestigationStep> findByCaseIdOrderBySeq(String caseId);
    List<InvestigationStep> findByRunIdOrderBySeq(String runId);
}
