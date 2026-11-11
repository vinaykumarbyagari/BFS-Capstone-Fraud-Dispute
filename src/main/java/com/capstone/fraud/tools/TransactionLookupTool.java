package com.capstone.fraud.tools;

import com.capstone.fraud.domain.FraudCase;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TransactionLookupTool implements EvidenceTool {

    private final FixtureDataService fixtures;

    public TransactionLookupTool(FixtureDataService fixtures) {
        this.fixtures = fixtures;
    }

    @Override
    public String name() {
        return "transaction_lookup";
    }

    @Override
    public String description() {
        return "Look up the disputed transaction by reference: amount, merchant, channel, country, timestamp";
    }

    @Override
    public Map<String, String> argumentSpec() {
        return Map.of("transactionRef", "the transaction reference, e.g. TXN-5001");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, FraudCase fraudCase) {
        String ref = argOr(args, "transactionRef", fraudCase.getTransactionRef());
        return fixtures.transaction(ref)
                .map(tx -> ToolResult.success(tx, "transaction " + ref + " found"))
                .orElse(ToolResult.notFound("no transaction found for ref " + ref));
    }

    static String argOr(Map<String, Object> args, String key, String fallback) {
        Object v = args == null ? null : args.get(key);
        return v == null || String.valueOf(v).isBlank() ? fallback : String.valueOf(v);
    }
}
