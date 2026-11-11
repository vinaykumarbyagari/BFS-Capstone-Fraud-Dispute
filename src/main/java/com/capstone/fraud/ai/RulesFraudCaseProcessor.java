package com.capstone.fraud.ai;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.EvidenceRecord;
import com.capstone.fraud.domain.FraudCase;
import com.capstone.fraud.domain.ProcessorMode;
import com.capstone.fraud.policy.PolicyRetriever;
import com.capstone.fraud.policy.RetrievalResult;
import com.capstone.fraud.repo.RecommendationRepository;
import com.capstone.fraud.service.CaseMemoryService;
import com.capstone.fraud.service.InvestigationTracer;
import com.capstone.fraud.tools.EvidenceToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic rules-mode processor: fixed tool plan, rules analyzer.
 * Always available; also the fallback when an AI mode fails.
 */
@Component
public class RulesFraudCaseProcessor extends AbstractFraudCaseProcessor {

    public RulesFraudCaseProcessor(EvidenceToolExecutor executor, PolicyRetriever retriever,
                                   InvestigationTracer tracer, RulesAnalyzer rulesAnalyzer,
                                   PromptFactory prompts, ContractValidator contractValidator,
                                   CaseMemoryService memory, RecommendationRepository recommendationRepo,
                                   ObjectMapper objectMapper, FraudProperties properties) {
        super(executor, retriever, tracer, rulesAnalyzer, prompts, contractValidator,
                memory, recommendationRepo, objectMapper, properties);
    }

    @Override
    public ProcessorMode mode() {
        return ProcessorMode.RULES;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    protected AnalysisResult analyze(FraudCase c, List<EvidenceRecord> evidence,
                                     RetrievalResult retrieval, String runId) {
        return new AnalysisResult(rulesAnalyzer.analyze(c, evidence, retrieval), null, true);
    }
}
