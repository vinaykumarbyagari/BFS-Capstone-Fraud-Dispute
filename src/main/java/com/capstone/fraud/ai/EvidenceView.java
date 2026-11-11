package com.capstone.fraud.ai;

import com.capstone.fraud.domain.EvidenceRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Read model over persisted evidence records: parses each record's
 * outputJson back into a map, keyed by tool name (first record wins).
 */
public class EvidenceView {

    private final Map<String, EvidenceRecord> records = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> payloads = new HashMap<>();

    public static EvidenceView of(List<EvidenceRecord> records, ObjectMapper mapper) {
        EvidenceView v = new EvidenceView();
        for (EvidenceRecord r : records) {
            v.records.putIfAbsent(r.getToolName(), r);
            if ("SUCCESS".equals(r.getOutcome()) && r.getOutputJson() != null) {
                try {
                    v.payloads.putIfAbsent(r.getToolName(),
                            mapper.readValue(r.getOutputJson(), new TypeReference<>() {}));
                } catch (Exception ignored) {
                }
            }
        }
        return v;
    }

    public boolean found(String tool) {
        EvidenceRecord r = records.get(tool);
        return r != null && "SUCCESS".equals(r.getOutcome());
    }

    public boolean called(String tool) {
        return records.containsKey(tool);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String tool) {
        return payloads.getOrDefault(tool, Map.of());
    }

    public Optional<Map<String, Object>> payload(String tool) {
        return Optional.ofNullable(payloads.get(tool));
    }

    public Collection<EvidenceRecord> records() {
        return records.values();
    }
}
