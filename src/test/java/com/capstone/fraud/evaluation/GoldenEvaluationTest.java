package com.capstone.fraud.evaluation;

import com.capstone.fraud.domain.*;
import com.capstone.fraud.dto.CaseIntakeRequest;
import com.capstone.fraud.dto.ProcessingOutcome;
import com.capstone.fraud.repo.EvidenceRecordRepository;
import com.capstone.fraud.repo.InvestigationStepRepository;
import com.capstone.fraud.service.CaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden evaluation scenarios: deterministic fixtures run end-to-end in
 * rules mode, asserting the expected route plus grounded citations and a
 * complete execution trace. Reproduces the demo script as tests.
 */
@SpringBootTest
class GoldenEvaluationTest {

    @Autowired
    CaseService caseService;
    @Autowired
    EvidenceRecordRepository evidenceRepo;
    @Autowired
    InvestigationStepRepository stepRepo;

    private CaseIntakeRequest req(String customerId, String last4, String txnRef,
                                  String merchant, String amount, Channel channel) {
        CaseIntakeRequest r = new CaseIntakeRequest();
        r.setCustomerId(customerId);
        r.setCardLast4(last4);
        r.setTransactionRef(txnRef);
        r.setMerchantName(merchant);
        r.setAmount(new BigDecimal(amount));
        r.setCurrency("USD");
        r.setChannel(channel);
        r.setDisputeType(DisputeType.UNAUTHORIZED_TRANSACTION);
        r.setDescription("customer denies making this transaction");
        return r;
    }

    @Test
    void fraudLikelyScenarioEscalates() {
        FraudCase c = caseService.intake(
                req("CUST-101", "4412", "TXN-5001", "SKYLINE ELECTRONICS", "1240.00",
                        Channel.CARD_NOT_PRESENT), "test");
        ProcessingOutcome o = caseService.investigate(c.getId(), "test");

        assertEquals(CaseRoute.ESCALATE_FRAUD_OPS, o.getOutput().getRoute());
        assertEquals(ProcessorMode.RULES, o.getRecommendation().getMode());
        assertFalse(o.getOutput().isAbstained());
        assertFalse(o.getOutput().getPolicyCitations().isEmpty());
        // at least four distinct evidence tools exercised
        assertTrue(evidenceRepo.findByCaseIdOrderBySeq(c.getId()).size() >= 4);
        // observable trace covers plan, tools, retrieval, decision
        var types = stepRepo.findByCaseIdOrderBySeq(c.getId()).stream()
                .map(InvestigationStep::getStepType).distinct().toList();
        assertTrue(types.contains(StepType.PLAN));
        assertTrue(types.contains(StepType.TOOL_CALL));
        assertTrue(types.contains(StepType.RETRIEVAL));
        assertTrue(types.contains(StepType.DECISION));
        assertEquals(CaseStatus.AWAITING_REVIEW, caseService.getCase(c.getId()).getStatus());
    }

    @Test
    void legitimateScenarioRecommendsDenial() {
        FraudCase c = caseService.intake(
                req("CUST-103", "2210", "TXN-5003", "CITY GROCER", "42.10",
                        Channel.CARD_PRESENT), "test");
        ProcessingOutcome o = caseService.investigate(c.getId(), "test");
        assertEquals(CaseRoute.RECOMMEND_DENIAL, o.getOutput().getRoute());
        assertEquals(Priority.LOW, o.getOutput().getPriority());
    }

    @Test
    void missingTransactionRequestsEvidence() {
        FraudCase c = caseService.intake(
                req("CUST-102", "8890", "TXN-9999", "NORDIC HOME GOODS", "86.40",
                        Channel.CARD_NOT_PRESENT), "test");
        ProcessingOutcome o = caseService.investigate(c.getId(), "test");
        assertEquals(CaseRoute.REQUEST_EVIDENCE, o.getOutput().getRoute());
        assertFalse(o.getOutput().getEvidenceRequests().isEmpty());
        assertEquals(CaseStatus.EVIDENCE_REQUESTED, caseService.getCase(c.getId()).getStatus());
    }

    @Test
    void highValueScenarioEscalatesUrgent() {
        FraudCase c = caseService.intake(
                req("CUST-104", "9034", "TXN-5004", "GLOBAL WIRE TRANSFER", "12000.00",
                        Channel.WIRE), "test");
        ProcessingOutcome o = caseService.investigate(c.getId(), "test");
        assertEquals(CaseRoute.ESCALATE_FRAUD_OPS, o.getOutput().getRoute());
        assertEquals(Priority.URGENT, o.getOutput().getPriority());
    }
}
