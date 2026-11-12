package com.capstone.fraud.tools;

import com.capstone.fraud.domain.EvidenceRecord;
import com.capstone.fraud.domain.FraudCase;
import com.capstone.fraud.dto.ToolCallSpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The tool executor is the single enforcement point for the bounded
 * catalog: unknown tools and calls beyond the budget are recorded as
 * REJECTED and never executed.
 */
@SpringBootTest
class ExecutorBoundariesTest {

    @Autowired
    EvidenceToolExecutor executor;

    private FraudCase caseStub() {
        FraudCase c = new FraudCase();
        c.setId("boundary-test-case");
        c.setCustomerId("CUST-101");
        c.setTransactionRef("TXN-5001");
        c.setMerchantName("SKYLINE ELECTRONICS");
        return c;
    }

    @Test
    void unknownToolIsRejectedNotExecuted() {
        List<EvidenceRecord> records = executor.executeAll(List.of(
                new ToolCallSpec("wire_funds", Map.of("amount", 1000000)),
                new ToolCallSpec("transaction_lookup", Map.of("transactionRef", "TXN-5001"))),
                caseStub(), "run-boundary-1");
        assertEquals(ToolResult.REJECTED, records.get(0).getOutcome());
        assertEquals(ToolResult.SUCCESS, records.get(1).getOutcome());
    }

    @Test
    void callsBeyondBudgetAreRejected() {
        List<ToolCallSpec> plan = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            plan.add(new ToolCallSpec("transaction_lookup", Map.of("transactionRef", "TXN-5001")));
        }
        List<EvidenceRecord> records = executor.executeAll(plan, caseStub(), "run-boundary-2");
        long executed = records.stream().filter(r -> ToolResult.SUCCESS.equals(r.getOutcome())).count();
        long rejected = records.stream().filter(r -> ToolResult.REJECTED.equals(r.getOutcome())).count();
        assertEquals(8, executed);          // fraud.evidence.max-tool-calls
        assertEquals(2, rejected);
    }

    @Test
    void catalogContainsAllEightTools() {
        var names = executor.catalog().stream().map(EvidenceTool::name).toList();
        assertTrue(names.containsAll(List.of(
                "transaction_lookup", "customer_profile", "device_login_history",
                "merchant_risk", "prior_case_history", "transaction_velocity",
                "amount_deviation", "sanctions_screening")));
    }
}
