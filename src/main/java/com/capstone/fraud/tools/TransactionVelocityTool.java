package com.capstone.fraud.tools;

import com.capstone.fraud.domain.FraudCase;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.capstone.fraud.tools.TransactionLookupTool.argOr;

/**
 * Counts the customer's synthetic transactions inside 10- and
 * 60-minute windows ending at the disputed transaction's timestamp.
 * Five or more transactions within 10 minutes is a velocity flag.
 */
@Component
public class TransactionVelocityTool implements EvidenceTool {

    private final FixtureDataService fixtures;

    public TransactionVelocityTool(FixtureDataService fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    public String name() {
        return "transaction_velocity";
    }

    @Override
    public String description() {
        return "Transaction velocity: counts of the customer's transactions in 10- and 60-minute "
                + "windows ending at the disputed transaction; flags 5+ within 10 minutes";
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
        List<Map<String, Object>> recent = fixtures.recentActivityFor(customerId);

        Instant anchor = fixtures.transaction(ref)
                .map(tx -> parseInstant(asString(tx.get("timestamp"))))
                .orElseGet(() -> recent.stream()
                        .map(r -> parseInstant(asString(r.get("timestamp"))))
                        .filter(Objects::nonNull)
                        .max(Instant::compareTo)
                        .orElse(null));

        Map<String, Object> out = new HashMap<>();
        out.put("found", !recent.isEmpty());
        out.put("customerId", customerId);
        out.put("recentCount", recent.size());
        if (anchor == null) {
            out.put("countLast10Min", 0);
            out.put("countLast60Min", 0);
            out.put("velocityFlag", false);
            return recent.isEmpty()
                    ? ToolResult.notFound("no recent activity for " + customerId)
                    : ToolResult.success(out, "velocity unknown — no timestamp anchor for " + customerId);
        }

        long last10 = 1 + recent.stream()
                .map(r -> parseInstant(asString(r.get("timestamp"))))
                .filter(t -> t != null && within(t, anchor, Duration.ofMinutes(10)))
                .count();
        long last60 = 1 + recent.stream()
                .map(r -> parseInstant(asString(r.get("timestamp"))))
                .filter(t -> t != null && within(t, anchor, Duration.ofMinutes(60)))
                .count();

        out.put("anchor", anchor.toString());
        out.put("countLast10Min", last10);
        out.put("countLast60Min", last60);
        out.put("velocityFlag", last10 >= 5);
        return ToolResult.success(out,
                last10 + " txn in 10min, " + last60 + " in 60min for " + customerId
                        + (last10 >= 5 ? " — velocity flag" : ""));
    }

    private boolean within(Instant t, Instant anchor, Duration window) {
        return !t.isAfter(anchor) && t.isAfter(anchor.minus(window));
    }

    private Instant parseInstant(String value) {
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
