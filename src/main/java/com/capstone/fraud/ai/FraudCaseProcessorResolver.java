package com.capstone.fraud.ai;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.ProcessorMode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Selects the active processor from fraud.processor.mode:
 *  - rules       -> deterministic rules engine
 *  - springai    -> Spring AI mode (falls back to rules if unavailable)
 *  - langchain4j -> LangChain4j mode (falls back to rules if unavailable)
 *  - auto        -> Spring AI when an API key is configured, else rules
 */
@Slf4j
@Component
public class FraudCaseProcessorResolver {

    private final Map<ProcessorMode, FraudCaseProcessor> byMode = new EnumMap<>(ProcessorMode.class);
    private final String configured;

    public FraudCaseProcessorResolver(List<FraudCaseProcessor> processors, FraudProperties properties) {
        processors.forEach(p -> byMode.put(p.mode(), p));
        this.configured = properties.getProcessor().getMode().trim().toLowerCase();
    }

    @PostConstruct
    void logSelection() {
        log.info("Fraud processor mode '{}' -> {}", configured, resolve().mode());
    }

    public FraudCaseProcessor resolve() {
        return switch (configured) {
            case "rules" -> byMode.get(ProcessorMode.RULES);
            case "springai" -> orRules(ProcessorMode.SPRING_AI);
            case "langchain4j" -> orRules(ProcessorMode.LANGCHAIN4J);
            default -> auto();
        };
    }

    private FraudCaseProcessor auto() {
        FraudCaseProcessor springAi = byMode.get(ProcessorMode.SPRING_AI);
        if (springAi != null && springAi.available()) return springAi;
        FraudCaseProcessor lc4j = byMode.get(ProcessorMode.LANGCHAIN4J);
        if (lc4j != null && lc4j.available()) return lc4j;
        return byMode.get(ProcessorMode.RULES);
    }

    private FraudCaseProcessor orRules(ProcessorMode wanted) {
        FraudCaseProcessor p = byMode.get(wanted);
        if (p != null && p.available()) return p;
        log.warn("Processor {} requested but unavailable (missing credentials?) — using rules mode", wanted);
        return byMode.get(ProcessorMode.RULES);
    }

    public String configuredMode() {
        return configured;
    }
}
