package com.capstone.fraud.repo;

import com.capstone.fraud.domain.ReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, String> {
    List<ReviewDecision> findByCaseIdOrderByCreatedAtDesc(String caseId);
    Optional<ReviewDecision> findFirstByCaseIdOrderByCreatedAtDesc(String caseId);
}
