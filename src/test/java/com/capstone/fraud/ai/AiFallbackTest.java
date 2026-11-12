package com.capstone.fraud.ai;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.*;
import com.capstone.fraud.dto.CaseIntakeRequest;
import com.capstone.fraud.dto.ProcessingOutcome;
import com.capstone.fraud.policy.PolicyRetriever;
import com.capstone.fraud.repo.InvestigationStepRepository;
import com.capstone.fraud.repo.RecommendationRepository;
import com.capstone.fraud.service.CaseMemoryService;
import com.capstone.fraud.service.CaseService;
import com.capstone.fraud.service.InvestigationTracer;
import com.capstone.fraud.tools.EvidenceToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * When the model call fails, the pipeline must degrade to
 * RULES_FALLBACK with a deterministic recommendation — never a 500.
 */
@SpringBootTest
class AiFallbackTest {

    @Autowired CaseService caseService;
    @Autowired EvidenceToolExecutor executor;
    @Autowired PolicyRetriever retriever;
    @Autowired InvestigationTracer tracer;
    @Autowired RulesAnalyzer rulesAnalyzer;
    @Autowired PromptFactory prompts;
    @Autowired ContractValidator contractValidator;
    @Autowired CaseMemoryService memory;
    @Autowired RecommendationRepository recommendationRepo;
    @Autowired InvestigationStepRepository stepRepo;
    @Autowired ObjectMapper objectMapper;
    @Autowired FraudProperties properties;

    @Test
    void modelFailureDegradesToRulesFallback() {
        FraudCaseProcessor failing = new AbstractAiFraudCaseProcessor(
                executor, retriever, tracer, rulesAnalyzer, prompts, contractValidator,
                memory, recommendationRepo, objectMapper, properties) {
            @Override
            public ProcessorMode mode() {
                return ProcessorMode.SPRING_AI;
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            protected String callModel(String systemPrompt, String userPrompt, String caseId) {
                throw new IllegalStateException("model endpoint down");
            }
        };

        CaseIntakeRequest req = new CaseIntakeRequest();
        req.setCustomerId("CUST-101");
        req.setCardLast4("4412");
        req.setTransactionRef("TXN-FALLBACK-1");
        req.setMerchantName("SKYLINE ELECTRONICS");
        req.setAmount(new BigDecimal("1240.00"));
        req.setCurrency("USD");
        req.setChannel(Channel.CARD_NOT_PRESENT);
        req.setDisputeType(DisputeType.UNAUTHORIZED_TRANSACTION);
        req.setDescription("fallback test");
        FraudCase c = caseService.intake(req, "test");

        ProcessingOutcome o = failing.investigate(c);

        assertEquals(ProcessorMode.RULES_FALLBACK, o.getRecommendation().getMode());
        // TXN-FALLBACK-1 is not in the fixtures, so rules deterministically
        // request evidence — the key point is a valid recommendation, not a 500.
        assertEquals(CaseRoute.REQUEST_EVIDENCE, o.getOutput().getRoute());
        assertFalse(o.getOutput().getEvidenceRequests().isEmpty());
        assertTrue(stepRepo.findByCaseIdOrderBySeq(c.getId()).stream()
                .anyMatch(s -> s.getStepType() == StepType.FALLBACK));
    }
}
