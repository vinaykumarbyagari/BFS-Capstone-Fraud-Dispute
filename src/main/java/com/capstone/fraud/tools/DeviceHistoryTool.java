package com.capstone.fraud.tools;

import com.capstone.fraud.domain.FraudCase;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DeviceHistoryTool implements EvidenceTool {

    private final FixtureDataService fixtures;

    public DeviceHistoryTool(FixtureDataService fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    public String name() {
        return "device_login_history";
    }

    @Override
    public String description() {
        return "Recent device and login signals: trusted devices, login geographies, failed attempts";
    }

    @Override
    public Map<String, String> argumentSpec() {
        return Map.of("customerId", "the customer identifier, e.g. CUST-101");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, FraudCase fraudCase) {
        String id = TransactionLookupTool.argOr(args, "customerId", fraudCase.getCustomerId());
        return fixtures.deviceProfile(id)
                .map(d -> ToolResult.success(d, "device history found for " + id))
                .orElse(ToolResult.notFound("no device history for " + id));
    }
}
