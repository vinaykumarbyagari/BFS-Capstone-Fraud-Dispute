package com.capstone.fraud.tools;

import com.capstone.fraud.domain.FraudCase;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CustomerProfileTool implements EvidenceTool {

    private final FixtureDataService fixtures;

    public CustomerProfileTool(FixtureDataService fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    public String name() {
        return "customer_profile";
    }

    @Override
    public String description() {
        return "Fetch the customer profile: tenure, home country, typical spend, KYC status";
    }

    @Override
    public Map<String, String> argumentSpec() {
        return Map.of("customerId", "the customer identifier, e.g. CUST-101");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, FraudCase fraudCase) {
        String id = TransactionLookupTool.argOr(args, "customerId", fraudCase.getCustomerId());
        return fixtures.customer(id)
                .map(c -> ToolResult.success(c, "customer " + id + " found"))
                .orElse(ToolResult.notFound("no customer found for id " + id));
    }
}
