package com.capstone.fraud.service;

import com.capstone.fraud.domain.InvestigationStep;
import com.capstone.fraud.domain.StepType;
import com.capstone.fraud.repo.InvestigationStepRepository;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Writes the ordered, persisted execution trace for an investigation run.
 * Every plan, tool call, retrieval, model call, validation and decision
 * becomes an {@link InvestigationStep} row.
 */
@Service
public class InvestigationTracer {

    private final InvestigationStepRepository steps;
    private final ConcurrentMap<String, AtomicInteger> seqCounters = new ConcurrentHashMap<>();

    public InvestigationTracer(InvestigationStepRepository steps) {
        this.steps = steps;
    }

    public InvestigationStep trace(String runId, String caseId, StepType type,
                                   String name, String input, String output) {
        return trace(runId, caseId, type, name, input, output, "OK", 0);
    }

    public InvestigationStep trace(String runId, String caseId, StepType type, String name,
                                   String input, String output, String status, long durationMs) {
        InvestigationStep s = new InvestigationStep();
        s.setRunId(runId);
        s.setCaseId(caseId);
        s.setStepType(type);
        s.setName(name);
        s.setInputSummary(truncate(input, 3900));
        s.setOutputSummary(truncate(output, 7900));
        s.setStatus(status);
        s.setDurationMs(durationMs);
        s.setSeq(seqCounters.computeIfAbsent(runId, k -> new AtomicInteger()).incrementAndGet());
        return steps.save(s);
    }

    /** Times a callable and records the step around it. */
    public <T> T timed(String runId, String caseId, StepType type, String name,
                       String input, Supplier<T> work) {
        long start = System.currentTimeMillis();
        try {
            T result = work.get();
            trace(runId, caseId, type, name, input, summarize(result), "OK",
                    System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException e) {
            trace(runId, caseId, type, name, input, String.valueOf(e.getMessage()), "ERROR",
                    System.currentTimeMillis() - start);
            throw e;
        }
    }

    private String summarize(Object result) {
        return result == null ? "" : truncate(String.valueOf(result), 2000);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…[truncated]";
    }
}
