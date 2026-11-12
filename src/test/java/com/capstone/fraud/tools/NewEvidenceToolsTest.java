package com.capstone.fraud.tools;

import com.capstone.fraud.domain.FraudCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the velocity, amount-deviation and sanctions-screening
 * evidence tools against the deterministic synthetic fixtures.
 */
class NewEvidenceToolsTest {

    private FixtureDataService fixtures;
    private TransactionVelocityTool velocity;
    private AmountDeviationTool deviation;
    private SanctionsScreeningTool screening;

    @BeforeEach
    void setup() {
        fixtures = new FixtureDataService(new ObjectMapper());
        fixtures.load();
        velocity = new TransactionVelocityTool(fixtures);
        deviation = new AmountDeviationTool(fixtures);
        screening = new SanctionsScreeningTool(fixtures);
    }

    private FraudCase fraudCase(String customerId, String txnRef, String merchant) {
        FraudCase c = new FraudCase();
        c.setCustomerId(customerId);
        c.setTransactionRef(txnRef);
        c.setMerchantName(merchant);
        return c;
    }

    @Test
    void velocityFlagsFivePlusWithinTenMinutes() {
        ToolResult r = velocity.execute(Map.of(
                "customerId", "CUST-101", "transactionRef", "TXN-5001"),
                fraudCase("CUST-101", "TXN-5001", "SKYLINE ELECTRONICS"));
        assertEquals(ToolResult.SUCCESS, r.getOutcome());
        assertTrue((long) r.getPayload().get("countLast10Min") >= 5);
        assertEquals(Boolean.TRUE, r.getPayload().get("velocityFlag"));
    }

    @Test
    void velocityCalmForLowActivityCustomer() {
        ToolResult r = velocity.execute(Map.of(
                "customerId", "CUST-103", "transactionRef", "TXN-5003"),
                fraudCase("CUST-103", "TXN-5003", "CITY GROCER"));
        assertEquals(ToolResult.SUCCESS, r.getOutcome());
        assertEquals(Boolean.FALSE, r.getPayload().get("velocityFlag"));
    }

    @Test
    void deviationFlagsLargeRatio() {
        ToolResult r = deviation.execute(Map.of(
                "customerId", "CUST-101", "transactionRef", "TXN-5001"),
                fraudCase("CUST-101", "TXN-5001", "SKYLINE ELECTRONICS"));
        assertEquals(ToolResult.SUCCESS, r.getOutcome());
        assertEquals(Boolean.TRUE, r.getPayload().get("deviationFlag"));
        assertTrue((double) r.getPayload().get("deviationRatio") > 5.0);
    }

    @Test
    void deviationCalmForNormalAmount() {
        ToolResult r = deviation.execute(Map.of(
                "customerId", "CUST-103", "transactionRef", "TXN-5003"),
                fraudCase("CUST-103", "TXN-5003", "CITY GROCER"));
        assertEquals(ToolResult.SUCCESS, r.getOutcome());
        assertEquals(Boolean.FALSE, r.getPayload().get("deviationFlag"));
    }

    @Test
    void screeningHitMandatesEscalation() {
        ToolResult r = screening.execute(Map.of(
                "customerId", "CUST-104", "merchantName", "GLOBAL WIRE TRANSFER"),
                fraudCase("CUST-104", "TXN-5004", "GLOBAL WIRE TRANSFER"));
        assertEquals(ToolResult.SUCCESS, r.getOutcome());
        assertEquals(Boolean.TRUE, r.getPayload().get("hit"));
        assertEquals(Boolean.TRUE, r.getPayload().get("mandatoryEscalation"));
        assertFalse(((java.util.List<?>) r.getPayload().get("matches")).isEmpty());
    }

    @Test
    void screeningClearForNormalEntities() {
        ToolResult r = screening.execute(Map.of(
                "customerId", "CUST-103", "merchantName", "CITY GROCER"),
                fraudCase("CUST-103", "TXN-5003", "CITY GROCER"));
        assertEquals(ToolResult.SUCCESS, r.getOutcome());
        assertEquals(Boolean.FALSE, r.getPayload().get("hit"));
        assertEquals(Boolean.FALSE, r.getPayload().get("mandatoryEscalation"));
    }
}
