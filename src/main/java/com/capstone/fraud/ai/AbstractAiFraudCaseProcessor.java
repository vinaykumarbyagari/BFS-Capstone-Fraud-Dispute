package com.capstone.fraud.ai;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.*;
import com.capstone.fraud.dto.ToolCallSpec;
import com.capstone.fraud.policy.PolicyRetriever;
import com.capstone.fraud.policy.RetrievalResult;
import com.capstone.fraud.repo.RecommendationRepository;
import com.capstone.fraud.service.CaseMemoryService;
import com.capstone.fraud.service.InvestigationTracer;
import com.capstone.fraud.tools.EvidenceToolExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared agent pipeline for model-backed modes. Subclasses supply the
 * framework-specific model call; this class supplies the agentic
 * structure — model-chosen tool planning, grounded analysis, schema
 * validation with one retry, and case-scoped memory injection.
 */
public abstract class AbstractAiFraudCaseProcessor extends AbstractFraudCaseProcessor {

    protected AbstractAiFraudCaseProcessor(EvidenceToolExecutor executor, PolicyRetriever retriever,
                                           InvestigationTracer tracer, RulesAnalyzer rulesAnalyzer,
                                           PromptFactory prompts, ContractValidator contractValidator,
                                           CaseMemoryService memory, RecommendationRepository recommendationRepo,
                                           ObjectMapper objectMapper, FraudProperties properties) {
        super(executor, retriever, tracer, rulesAnalyzer, prompts, contractValidator,
                memory, recommendationRepo, objectMapper, properties);
    }

    /** Framework-specific model invocation; implementations must not swallow errors. */
    protected abstract String callModel(String systemPrompt, String userPrompt);

    @Override
    protected List<ToolCallSpec> planToolCalls(FraudCase c, String runId) {
        if (!available()) {
            return super.planToolCalls(c, runId);
        }
        try {
            String raw = tracer.timed(runId, c.getId(), StepType.MODEL_CALL, "tool-plan",
                    "plan request", () -> callModel(prompts.planSystem(), prompts.planUser(c)));
            List<ToolCallSpec> plan = parsePlan(raw);
            if (plan.isEmpty()) {
                throw new IllegalStateException("planner returned no tool calls");
            }
            tracer.trace(runId, c.getId(), StepType.PLAN, "tool-plan",
                    "model-driven", plan.size() + " tool call(s): "
                            + plan.stream().map(ToolCallSpec::getTool).toList());
            return plan;
        } catch (RuntimeException e) {
            tracer.trace(runId, c.getId(), StepType.PLAN, "tool-plan-fallback",
                    "model-driven", "planner failed: " + e.getMessage() + " — using deterministic plan",
                    "ERROR", 0);
            return super.planToolCalls(c, runId);
        }
    }

    private List<ToolCallSpec> parsePlan(String raw) {
        String json = JsonExtractor.extractObject(raw)
                .orElseThrow(() -> new IllegalStateException("no JSON in planner output"));
        try {
            Map<String, Object> doc = objectMapper.readValue(json, new TypeReference<>() {});
            Object calls = doc.get("toolCalls");
            if (!(calls instanceof List<?> list)) return List.of();
            List<ToolCallSpec> out = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Object tool = m.get("tool");
                if (tool == null) continue;
                ToolCallSpec spec = new ToolCallSpec();
                spec.setTool(String.valueOf(tool));
                Object args = m.get("arguments");
                if (args instanceof Map<?, ?> am) {
                    am.forEach((k, v) -> spec.getArguments().put(String.valueOf(k), v));
                }
                out.add(spec);
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse planner output: " + e.getMessage(), e);
        }
    }

    @Override
    protected AnalysisResult analyze(FraudCase c, List<EvidenceRecord> evidence,
                                     RetrievalResult retrieval, String runId) {
        String system = prompts.analyzeSystem();
        String user = prompts.analyzeUser(c, evidence, retrieval.getPassages(),
                memory.recent(c.getId(), 10));

        String raw = tracer.timed(runId, c.getId(), StepType.MODEL_CALL, "recommendation",
                "analyze request", () -> callModel(system, user));
        String json = JsonExtractor.extractObject(raw)
                .orElseThrow(() -> new IllegalStateException("model returned no JSON object"));

        List<String> errors = contractValidator.validate(json);
        if (!errors.isEmpty()) {
            tracer.trace(runId, c.getId(), StepType.VALIDATION, "contract-check",
                    "attempt 1", String.join("; ", errors), "ERROR", 0);
            // one correction retry with the validation errors fed back
            String retryUser = user + "\n\nPrevious output failed schema validation: "
                    + String.join("; ", errors) + "\nReturn a corrected JSON object only.";
            raw = tracer.timed(runId, c.getId(), StepType.MODEL_CALL, "recommendation-retry",
                    "analyze request (retry)", () -> callModel(system, retryUser));
            json = JsonExtractor.extractObject(raw)
                    .orElseThrow(() -> new IllegalStateException("retry returned no JSON object"));
            errors = contractValidator.validate(json);
            if (!errors.isEmpty()) {
                tracer.trace(runId, c.getId(), StepType.VALIDATION, "contract-check",
                        "attempt 2", String.join("; ", errors), "ERROR", 0);
                throw new IllegalStateException("model output failed contract validation after retry");
            }
        }
        tracer.trace(runId, c.getId(), StepType.VALIDATION, "contract-check",
                "recommendation.schema.json", "valid");
        return new AnalysisResult(contractValidator.parse(json), raw, true);
    }
}
