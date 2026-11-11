package com.capstone.fraud.repo;

import com.capstone.fraud.domain.FraudCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FraudCaseRepository extends JpaRepository<FraudCase, String> {
    Optional<FraudCase> findByCaseNumber(String caseNumber);
    List<FraudCase> findAllByOrderByCreatedAtDesc();
    List<FraudCase> findByStatusOrderByCreatedAtDesc(com.capstone.fraud.domain.CaseStatus status);
    long countByStatus(com.capstone.fraud.domain.CaseStatus status);
}
