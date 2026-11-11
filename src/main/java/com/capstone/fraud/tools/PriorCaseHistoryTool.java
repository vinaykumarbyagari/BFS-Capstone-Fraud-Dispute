package com.capstone.fraud.tools;

import com.capstone.fraud.domain.FraudCase;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PriorCaseHistoryTool implements EvidenceTool {

    private final FixtureDataService fixtures;

    public PriorCaseHistoryTool(FixtureDataService fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    public String name() {
        return "prior_case_history";
    }

    @Override
    public String description() {
        return "Prior fraud/dispute cases for this customer: counts, types and outcomes";
    }

    @Override
    public Map<String, String> argumentSpec() {
        return Map.of("customerId", "the customer identifier, e.g. CUST-101");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, FraudCase fraudCase) {
        String id = TransactionLookupTool.argOr(args, "customerId", fraudCase.getCustomerId());
        List<Map<String, Object>> cases = fixtures.priorCasesFor(id);
        if (cases.isEmpty()) {
            return ToolResult.notFound("no prior cases for " + id);
        }
        return ToolResult.success(
                Map.of("customerId", id, "priorCaseCount", cases.size(), "cases", cases),
                cases.size() + " prior case(s) for " + id);
    }
}
