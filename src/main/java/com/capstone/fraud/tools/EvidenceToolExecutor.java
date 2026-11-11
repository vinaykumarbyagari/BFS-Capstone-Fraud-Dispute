package com.capstone.fraud.tools;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.EvidenceRecord;
import com.capstone.fraud.domain.FraudCase;
import com.capstone.fraud.domain.StepType;
import com.capstone.fraud.dto.ToolCallSpec;
import com.capstone.fraud.repo.EvidenceRecordRepository;
import com.capstone.fraud.service.InvestigationTracer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Executes evidence tool calls against the bounded tool catalog.
 * Every invocation — allowed, unknown or over-budget — is persisted
 * to the evidence table and the execution trace. This is the only
 * path through which tools may run.
 */
@Slf4j
@Component
public class EvidenceToolExecutor {

    private final Map<String, EvidenceTool> catalog;
    private final EvidenceRecordRepository evidenceRepo;
    private final InvestigationTracer tracer;
    private final ObjectMapper objectMapper;
    private final int maxToolCalls;

    public EvidenceToolExecutor(List<EvidenceTool> tools,
                                EvidenceRecordRepository evidenceRepo,
                                InvestigationTracer tracer,
                                ObjectMapper objectMapper,
                                FraudProperties properties) {
        this.catalog = tools.stream().collect(Collectors.toMap(EvidenceTool::name, t -> t));
        this.evidenceRepo = evidenceRepo;
        this.tracer = tracer;
        this.objectMapper = objectMapper;
        this.maxToolCalls = properties.getEvidence().getMaxToolCalls();
    }

    public List<EvidenceTool> catalog() {
        return List.copyOf(catalog.values());
    }

    /** Catalog description used in planner prompts. */
    public String catalogDescription() {
        StringBuilder sb = new StringBuilder();
        catalog.values().forEach(t -> {
            sb.append("- ").append(t.name()).append(": ").append(t.description());
            sb.append(" args{");
            t.argumentSpec().forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
            sb.append("}\n");
        });
        return sb.toString();
    }

    /**
     * Run a tool plan. Calls beyond the budget or referencing unknown
     * tools are recorded as REJECTED, never executed.
     */
    public List<EvidenceRecord> executeAll(List<ToolCallSpec> calls, FraudCase fraudCase, String runId) {
        List<EvidenceRecord> records = new ArrayList<>();
        int executed = 0;
        int seq = 0;
        for (ToolCallSpec call : calls) {
            seq++;
            EvidenceTool tool = catalog.get(call.getTool());
            if (tool == null) {
                records.add(persist(fraudCase, runId, call, seq,
                        ToolResult.rejected("unknown tool '" + call.getTool() + "'"), 0));
                continue;
            }
            if (executed >= maxToolCalls) {
                records.add(persist(fraudCase, runId, call, seq,
                        ToolResult.rejected("tool call budget exhausted (" + maxToolCalls + ")"), 0));
                continue;
            }
            executed++;
            long start = System.currentTimeMillis();
            ToolResult result;
            try {
                result = tool.execute(call.getArguments(), fraudCase);
            } catch (RuntimeException e) {
                log.warn("Tool {} failed: {}", call.getTool(), e.getMessage());
                result = ToolResult.error("tool error: " + e.getMessage());
            }
            long duration = System.currentTimeMillis() - start;
            records.add(persist(fraudCase, runId, call, seq, result, duration));
        }
        return records;
    }

    private EvidenceRecord persist(FraudCase fraudCase, String runId, ToolCallSpec call,
                                   int seq, ToolResult result, long durationMs) {
        String argsJson = writeJson(call.getArguments());
        String outJson = writeJson(result.getPayload());

        EvidenceRecord r = new EvidenceRecord();
        r.setCaseId(fraudCase.getId());
        r.setRunId(runId);
        r.setToolName(call.getTool());
        r.setArgumentsJson(argsJson);
        r.setOutcome(result.getOutcome());
        r.setOutputJson(outJson);
        r.setDurationMs(durationMs);
        r.setSeq(seq);
        evidenceRepo.save(r);

        tracer.trace(runId, fraudCase.getId(), StepType.TOOL_CALL, call.getTool(),
                argsJson, result.getSummary() + " | " + truncate(outJson, 1800),
                result.getOutcome().equals(ToolResult.ERROR) || result.getOutcome().equals(ToolResult.REJECTED)
                        ? "ERROR" : "OK",
                durationMs);
        return r;
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
