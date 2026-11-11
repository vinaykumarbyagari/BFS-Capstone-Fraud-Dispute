package com.capstone.fraud.repo;

import com.capstone.fraud.domain.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, String> {
    List<Recommendation> findByCaseIdOrderByCreatedAtDesc(String caseId);
    Optional<Recommendation> findFirstByCaseIdOrderByCreatedAtDesc(String caseId);
}
