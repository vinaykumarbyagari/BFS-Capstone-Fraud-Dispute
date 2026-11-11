package com.capstone.fraud.tools;

import com.capstone.fraud.domain.FraudCase;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MerchantRiskTool implements EvidenceTool {

    private final FixtureDataService fixtures;

    public MerchantRiskTool(FixtureDataService fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    public String name() {
        return "merchant_risk";
    }

    @Override
    public String description() {
        return "Merchant risk profile: category, risk score, prior fraud report count, country";
    }

    @Override
    public Map<String, String> argumentSpec() {
        return Map.of("merchantName", "merchant name as it appears on the statement");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, FraudCase fraudCase) {
        String name = TransactionLookupTool.argOr(args, "merchantName", fraudCase.getMerchantName());
        return fixtures.merchant(name)
                .map(m -> ToolResult.success(m, "merchant profile found for " + name))
                .orElse(ToolResult.notFound("no merchant profile for " + name));
    }
}
