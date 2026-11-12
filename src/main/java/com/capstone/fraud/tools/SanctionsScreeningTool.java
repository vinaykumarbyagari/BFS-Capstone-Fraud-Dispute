package com.capstone.fraud.tools;

import com.capstone.fraud.domain.FraudCase;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.capstone.fraud.tools.TransactionLookupTool.argOr;

/**
 * Synthetic sanctions/PEP screening for the customer and merchant.
 * Any hit is a mandatory-escalation signal surfaced to the human
 * reviewer — never acted on autonomously.
 */
@Component
public class SanctionsScreeningTool implements EvidenceTool {

    private final FixtureDataService fixtures;

    public SanctionsScreeningTool(FixtureDataService fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    public String name() {
        return "sanctions_screening";
    }

    @Override
    public String description() {
        return "Screens customer and merchant against the synthetic sanctions/PEP watchlist; "
                + "any match mandates escalation to compliance review";
    }

    @Override
    public Map<String, String> argumentSpec() {
        return Map.of(
                "customerId", "customer identifier, e.g. CUST-101",
                "merchantName", "merchant name as it appears on the statement");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, FraudCase fraudCase) {
        String customerId = argOr(args, "customerId", fraudCase.getCustomerId());
        String merchantName = argOr(args, "merchantName", fraudCase.getMerchantName());
        List<Map<String, Object>> hits = fixtures.screeningHitsFor(customerId, merchantName);

        Map<String, Object> out = new HashMap<>();
        out.put("customerId", customerId);
        out.put("merchantName", merchantName);
        out.put("hit", !hits.isEmpty());
        out.put("mandatoryEscalation", !hits.isEmpty());
        out.put("listsChecked", List.of("SANCTIONS_WATCHLIST", "PEP"));
        out.put("matches", hits);
        return ToolResult.success(out, hits.isEmpty()
                ? "no screening hits for " + customerId + " / " + merchantName
                : hits.size() + " screening hit(s) — mandatory escalation");
    }
}
