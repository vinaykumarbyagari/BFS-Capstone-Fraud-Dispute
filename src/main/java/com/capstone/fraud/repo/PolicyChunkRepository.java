package com.capstone.fraud.repo;

import com.capstone.fraud.domain.PolicyChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyChunkRepository extends JpaRepository<PolicyChunk, String> {
    List<PolicyChunk> findByPolicyId(String policyId);
    long countByPolicyId(String policyId);
}
