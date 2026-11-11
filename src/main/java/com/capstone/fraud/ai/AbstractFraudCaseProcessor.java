package com.capstone.fraud.ai;

import com.capstone.fraud.domain.*;
import com.capstone.fraud.dto.PolicyCitation;
import com.capstone.fraud.dto.ProcessingOutcome;
import com.capstone.fraud.dto.RecommendationOutput;
import com.capstone.fraud.dto.ToolCallSpec;
import com.capstone.fraud.policy.PolicyRetriever;
import com.capstone.fraud.policy.RetrievalResult;
import com.capstone.fraud.repo.RecommendationRepository;
import com.capstone.fraud.service.CaseMemoryService;
import com.capstone.fraud.service.InvestigationTracer;
import com.capstone.fraud.tools.EvidenceToolExecutor;
import com.capstone.fraud.config.FraudProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Shared investigation pipeline for every processor mode:
 * plan -> execute tools -> retrieve policy -> analyze -> validate ->
 * deterministic safety net -> persist + trace.
 *
 * Modes differ only in the plan/analysis steps; the safety net below
 * cannot be bypassed by model output.
 */
@Slf4j
public abstract class AbstractFraudCaseProcessor implements FraudCaseProcessor {

    protected final EvidenceToolExecutor executor;
    protected final PolicyRetriever retriever;
    protected final InvestigationTracer tracer;
    protected final RulesAnalyzer rulesAnalyzer;
    protected final PromptFactory prompts;
    protected final ContractValidator contractValidator;
    protected final CaseMemoryService memory;
    protected final RecommendationRepository recommendationRepo;
    protected final ObjectMapper objectMapper;
    protected final FraudProperties properties;

    protected AbstractFraudCaseProcessor(EvidenceToolExecutor executor, PolicyRetriever retriever,
                                         InvestigationTracer tracer, RulesAnalyzer rulesAnalyzer,
                                         PromptFactory prompts, ContractValidator contractValidator,
                                         CaseMemoryService memory, RecommendationRepository recommendationRepo,
                                         ObjectMapper objectMapper, FraudProperties properties) {
        this.executor = executor;
        this.retriever = retriever;
        this.tracer = tracer;
        this.rulesAnalyzer = rulesAnalyzer;
        this.prompts = prompts;
        this.contractValidator = contractValidator;
        this.memory = memory;
        this.recommendationRepo = recommendationRepo;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public ProcessingOutcome investigate(FraudCase fraudCase) {
        String runId = UUID.randomUUID().toString();
        String caseId = fraudCase.getId();
        tracer.trace(runId, caseId, StepType.PLAN, "investigation-start",
                "case " + fraudCase.getCaseNumber(), "mode=" + mode());

        // 1. plan tool calls (deterministic in rules mode, model-driven in AI modes)
        List<ToolCallSpec> plan = planToolCalls(fraudCase, runId);

        // 2. execute against the bounded catalog — every call is traced
        List<EvidenceRecord> evidence = executor.executeAll(plan, fraudCase, runId);

        // 3. policy retrieval
        String query = retrievalQuery(fraudCase, evidence);
        RetrievalResult retrieval = tracer.timed(runId, caseId, StepType.RETRIEVAL,
                "policy-retrieval", query,
                () -> retriever.retrieve(query, properties.getRetrieval().getTopK()));

        // 4. analysis -> contract output; AI failures degrade to rules
        AnalysisResult analysis;
        ProcessorMode actualMode = mode();
        String modelName = modelName();
        try {
            analysis = analyze(fraudCase, evidence, retrieval, runId);
        } catch (RuntimeException e) {
            log.warn("Processor {} failed for case {}: {}", mode(), fraudCase.getCaseNumber(), e.getMessage());
            tracer.trace(runId, caseId, StepType.FALLBACK, "rules-fallback",
                    "mode=" + mode(), "error: " + e.getMessage(), "OK", 0);
            analysis = new AnalysisResult(rulesAnalyzer.analyze(fraudCase, evidence, retrieval), null, true);
            actualMode = ProcessorMode.RULES_FALLBACK;
            modelName = "rules-engine";
        }

        RecommendationOutput output = analysis.output();

        // 5. deterministic safety net — model output cannot bypass this
        enforceSafetyNet(output, retrieval);
        filterUngroundedCitations(output, retrieval, runId, caseId);

        // 6. persist recommendation + memory + decision trace
        Recommendation rec = persistRecommendation(fraudCase, runId, actualMode, output, analysis, modelName);
        persistMemory(fraudCase, output);
        tracer.trace(runId, caseId, StepType.DECISION, "recommendation",
                "mode=" + actualMode,
                output.getRoute() + " / " + output.getPriority()
                        + " confidence=" + output.getConfidence()
                        + (output.isAbstained() ? " [abstained]" : ""));

        return new ProcessingOutcome(runId, rec, output);
    }

    /** Deterministic plan used by rules mode and as the AI planner fallback. */
    protected List<ToolCallSpec> planToolCalls(FraudCase c, String runId) {
        List<ToolCallSpec> plan = List.of(
                new ToolCallSpec("transaction_lookup", Map.of("transactionRef", safe(c.getTransactionRef()))),
                new ToolCallSpec("customer_profile", Map.of("customerId", safe(c.getCustomerId()))),
                new ToolCallSpec("device_login_history", Map.of("customerId", safe(c.getCustomerId()))),
                new ToolCallSpec("merchant_risk", Map.of("merchantName", safe(c.getMerchantName()))),
                new ToolCallSpec("prior_case_history", Map.of("customerId", safe(c.getCustomerId()))));
        tracer.trace(runId, c.getId(), StepType.PLAN, "tool-plan",
                "deterministic", plan.size() + " tool calls planned");
        return plan;
    }

    protected abstract AnalysisResult analyze(FraudCase c, List<EvidenceRecord> evidence,
                                              RetrievalResult retrieval, String runId);

    protected String modelName() {
        return "rules-engine";
    }

    protected String retrievalQuery(FraudCase c, List<EvidenceRecord> evidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("payment fraud dispute ")
                .append(safe(c.getDisputeType() == null ? "" : c.getDisputeType().name().replace('_', ' ')))
                .append(' ').append(safe(c.getChannel() == null ? "" : c.getChannel().name().replace('_', ' ')))
                .append(' ').append(safe(c.getMerchantName()))
                .append(' ').append(safe(c.getDescription()))
                .append(" unauthorized transaction routing escalation evidence requirements review");
        return sb.toString();
    }

    /**
     * Non-negotiable guardrails applied after analysis:
     * insufficient policy context or an abstaining model forces
     * MANUAL_REVIEW — model output can never bypass this.
     */
    protected void enforceSafetyNet(RecommendationOutput output, RetrievalResult retrieval) {
        if (!retrieval.isSufficient()) {
            output.setAbstained(true);
            output.setAbstainReason(String.format(
                    "policy retrieval insufficient (max score %.3f < minimum %.3f)",
                    retrieval.getMaxScore(), properties.getRetrieval().getMinScore()));
        }
        if (output.isAbstained() && recommendsAction(output.getRoute())) {
            output.setRoute(CaseRoute.MANUAL_REVIEW);
            if (output.getAbstainReason() == null || output.getAbstainReason().isBlank()) {
                output.setAbstainReason("model abstained; routed to manual review");
            }
        }
        if (output.getRoute() == null) {
            output.setRoute(CaseRoute.MANUAL_REVIEW);
            output.setAbstained(true);
            output.setAbstainReason("no route produced");
        }
    }

    /** Drops citations the model invented; a grounded route requires real passages. */
    private void filterUngroundedCitations(RecommendationOutput output, RetrievalResult retrieval,
                                           String runId, String caseId) {
        Set<String> validIds = new HashSet<>();
        retrieval.getPassages().forEach(p -> validIds.add(p.getChunk().getPolicyId()));
        List<PolicyCitation> kept = new ArrayList<>();
        int dropped = 0;
        for (PolicyCitation c : output.getPolicyCitations()) {
            if (c.getPolicyId() != null && validIds.contains(c.getPolicyId())) {
                kept.add(c);
            } else {
                dropped++;
            }
        }
        output.setPolicyCitations(kept);
        if (dropped > 0) {
            tracer.trace(runId, caseId, StepType.VALIDATION, "citation-filter",
                    "retrieved ids " + validIds, dropped + " ungrounded citation(s) dropped");
        }
        if (!output.isAbstained() && recommendsAction(output.getRoute()) && kept.isEmpty()) {
            output.setAbstained(true);
            output.setRoute(CaseRoute.MANUAL_REVIEW);
            output.setAbstainReason("route lacked any grounded policy citation after filtering");
            tracer.trace(runId, caseId, StepType.VALIDATION, "forced-abstention",
                    "no grounded citations", "route reset to MANUAL_REVIEW");
        }
    }

    private boolean recommendsAction(CaseRoute route) {
        return route == CaseRoute.RECOMMEND_REFUND || route == CaseRoute.RECOMMEND_DENIAL
                || route == CaseRoute.ESCALATE_FRAUD_OPS;
    }

    private Recommendation persistRecommendation(FraudCase c, String runId, ProcessorMode actualMode,
                                                 RecommendationOutput output, AnalysisResult analysis,
                                                 String modelName) {
        Recommendation rec = new Recommendation();
        rec.setCaseId(c.getId());
        rec.setRunId(runId);
        rec.setMode(actualMode);
        rec.setRoute(output.getRoute());
        rec.setPriority(output.getPriority());
        rec.setConfidence(output.getConfidence());
        rec.setSummary(output.getSummary());
        rec.setReasonsJson(json(output.getReasons()));
        rec.setCitationsJson(json(output.getPolicyCitations()));
        rec.setEvidenceRequestsJson(json(output.getEvidenceRequests()));
        rec.setAbstained(output.isAbstained());
        rec.setAbstainReason(output.getAbstainReason());
        rec.setRawOutput(analysis.rawOutput());
        rec.setSchemaValid(analysis.schemaValid());
        rec.setModelName(modelName);
        return recommendationRepo.save(rec);
    }

    private void persistMemory(FraudCase c, RecommendationOutput output) {
        memory.append(c.getId(), CaseMessage.ROLE_USER,
                "Investigation run requested for case " + c.getCaseNumber());
        memory.append(c.getId(), CaseMessage.ROLE_ASSISTANT,
                "Recommendation: " + output.getRoute() + " | priority " + output.getPriority()
                        + " | " + safe(output.getSummary()));
    }

    protected String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    protected static String safe(String s) {
        return s == null ? "" : s;
    }
}
