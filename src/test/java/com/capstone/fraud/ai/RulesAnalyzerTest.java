package com.capstone.fraud.ai;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.*;
import com.capstone.fraud.dto.RecommendationOutput;
import com.capstone.fraud.policy.RetrievalResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RulesAnalyzerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RulesAnalyzer analyzer = new RulesAnalyzer(new FraudProperties(), mapper);
    private final RetrievalResult grounded = new RetrievalResult(List.of(), true, 0.5);

    private EvidenceRecord record(String tool, String outcome, Map<String, Object> payload) {
        EvidenceRecord r = new EvidenceRecord();
        r.setToolName(tool);
        r.setOutcome(outcome);
        r.setOutputJson(write(payload));
        return r;
    }

    private String write(Map<String, Object> m) {
        try {
            return mapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    private FraudCase baseCase() {
        FraudCase c = new FraudCase();
        c.setCaseNumber("FC-T");
        c.setCustomerId("CUST-X");
        c.setCardLast4("1234");
        c.setTransactionRef("TXN-X");
        c.setAmount(new BigDecimal("1240.00"));
        c.setCurrency("USD");
        c.setChannel(Channel.CARD_NOT_PRESENT);
        c.setDisputeType(DisputeType.UNAUTHORIZED_TRANSACTION);
        return c;
    }

    @Test
    void requestsEvidenceWhenTransactionMissing() {
        List<EvidenceRecord> ev = List.of(
                record("transaction_lookup", "NOT_FOUND", Map.of("found", false)),
                record("customer_profile", "SUCCESS", Map.of("customerId", "CUST-X", "country", "US")));
        RecommendationOutput out = analyzer.analyze(baseCase(), ev, grounded);
        assertEquals(CaseRoute.REQUEST_EVIDENCE, out.getRoute());
        assertFalse(out.getEvidenceRequests().isEmpty());
    }

    @Test
    void escalatesOnStrongFraudSignals() {
        List<EvidenceRecord> ev = List.of(
                record("transaction_lookup", "SUCCESS", Map.of("ref", "TXN-X", "amount", 1240)),
                record("customer_profile", "SUCCESS", Map.of("country", "US", "kycStatus", "VERIFIED")),
                record("merchant_risk", "SUCCESS", Map.of("riskScore", 0.82, "priorFraudReports", 14)),
                record("device_login_history", "SUCCESS", Map.of("recentLogins", List.of(
                        Map.of("success", true, "trusted", false, "country", "RO"),
                        Map.of("success", false, "trusted", false, "country", "RO"),
                        Map.of("success", false, "trusted", false, "country", "RO")))),
                record("prior_case_history", "SUCCESS", Map.of("priorCaseCount", 1)));
        RecommendationOutput out = analyzer.analyze(baseCase(), ev, grounded);
        assertEquals(CaseRoute.ESCALATE_FRAUD_OPS, out.getRoute());
        assertTrue(out.getReasons().size() >= 4);
    }

    @Test
    void deniesWhenOnlyLegitSignals() {
        FraudCase c = baseCase();
        c.setAmount(new BigDecimal("42.10"));
        c.setChannel(Channel.CARD_PRESENT);
        List<EvidenceRecord> ev = List.of(
                record("transaction_lookup", "SUCCESS", Map.of("ref", "TXN-X", "amount", 42.1)),
                record("customer_profile", "SUCCESS", Map.of("country", "US", "kycStatus", "VERIFIED")),
                record("merchant_risk", "SUCCESS", Map.of("riskScore", 0.05, "priorFraudReports", 0)),
                record("device_login_history", "SUCCESS", Map.of("recentLogins", List.of(
                        Map.of("success", true, "trusted", true, "country", "US")))),
                record("prior_case_history", "NOT_FOUND", Map.of()));
        RecommendationOutput out = analyzer.analyze(c, ev, grounded);
        assertEquals(CaseRoute.RECOMMEND_DENIAL, out.getRoute());
        assertEquals(Priority.LOW, out.getPriority());
    }

    @Test
    void escalatesUrgentOnVeryHighAmount() {
        FraudCase c = baseCase();
        c.setAmount(new BigDecimal("12000.00"));
        c.setChannel(Channel.WIRE);
        List<EvidenceRecord> ev = List.of(
                record("transaction_lookup", "SUCCESS", Map.of("ref", "TXN-X", "amount", 12000)),
                record("customer_profile", "SUCCESS", Map.of("country", "US", "kycStatus", "VERIFIED")),
                record("merchant_risk", "SUCCESS", Map.of("riskScore", 0.2)),
                record("device_login_history", "SUCCESS", Map.of("recentLogins", List.of(
                        Map.of("success", true, "trusted", true, "country", "US")))));
        RecommendationOutput out = analyzer.analyze(c, ev, grounded);
        assertEquals(CaseRoute.ESCALATE_FRAUD_OPS, out.getRoute());
        assertEquals(Priority.URGENT, out.getPriority());
    }
}
