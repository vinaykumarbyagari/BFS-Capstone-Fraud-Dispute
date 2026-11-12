package com.capstone.fraud.tools;

import com.capstone.fraud.domain.FraudCase;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.capstone.fraud.tools.TransactionLookupTool.argOr;

/**
 * Compares the disputed amount against the customer's synthetic
 * transaction baseline (median of recent activity). Ratios above 5x
 * the baseline are flagged.
 */
@Component
public class AmountDeviationTool implements EvidenceTool {

    private static final double FLAG_RATIO = 5.0;

    private final FixtureDataService fixtures;

    public AmountDeviationTool(FixtureDataService fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    public String name() {
        return "amount_deviation";
    }

    @Override
    public String description() {
        return "Amount deviation: compares the disputed amount to the customer's recent-transaction "
                + "median baseline; flags amounts more than 5x the baseline";
    }

    @Override
    public Map<String, String> argumentSpec() {
        return Map.of(
                "customerId", "customer identifier, e.g. CUST-101",
                "transactionRef", "the disputed transaction reference, e.g. TXN-5001");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, FraudCase fraudCase) {
        String customerId = argOr(args, "customerId", fraudCase.getCustomerId());
        String ref = argOr(args, "transactionRef", fraudCase.getTransactionRef());

        double current = fixtures.transaction(ref)
                .map(tx -> toDouble(tx.get("amount")))
                .orElse(0.0);
        List<Double> amounts = fixtures.recentActivityFor(customerId).stream()
                .map(r -> toDouble(r.get("amount")))
                .sorted(Comparator.naturalOrder())
                .toList();
        double baseline = amounts.isEmpty() ? 0.0
                : amounts.size() % 2 == 1
                    ? amounts.get(amounts.size() / 2)
                    : (amounts.get(amounts.size() / 2 - 1) + amounts.get(amounts.size() / 2)) / 2.0;

        Map<String, Object> out = new HashMap<>();
        out.put("customerId", customerId);
        out.put("currentAmount", current);
        out.put("baselineMedian", round2(baseline));
        out.put("sampleSize", amounts.size());
        if (baseline > 0 && current > 0) {
            double ratio = current / baseline;
            out.put("deviationRatio", round2(ratio));
            out.put("deviationFlag", ratio > FLAG_RATIO);
            return ToolResult.success(out,
                    String.format("amount %.2f is %.1fx the %.2f median baseline",
                            current, ratio, baseline));
        }
        out.put("deviationRatio", null);
        out.put("deviationFlag", false);
        return current <= 0 && amounts.isEmpty()
                ? ToolResult.notFound("no transaction or activity baseline for " + customerId)
                : ToolResult.success(out, "insufficient baseline to compute deviation");
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) {
            try {
                return Double.parseDouble(String.valueOf(o));
            } catch (NumberFormatException ignored) { }
        }
        return 0.0;
    }
}
