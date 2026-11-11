package com.capstone.fraud.repo;

import com.capstone.fraud.domain.EvidenceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRecordRepository extends JpaRepository<EvidenceRecord, String> {
    List<EvidenceRecord> findByCaseIdOrderBySeq(String caseId);
    List<EvidenceRecord> findByRunIdOrderBySeq(String runId);
}
