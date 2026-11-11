package com.capstone.fraud.ai;

import com.capstone.fraud.domain.ProcessorMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test: all three implementations exist behind the
 * framework-neutral interface; AI modes are unavailable without a key,
 * so the resolver safely selects rules mode.
 */
@SpringBootTest
class ProcessorContractTest {

    @Autowired
    List<FraudCaseProcessor> processors;
    @Autowired
    FraudCaseProcessorResolver resolver;

    @Test
    void allThreeModesAreRegistered() {
        var modes = processors.stream().map(FraudCaseProcessor::mode).toList();
        assertTrue(modes.contains(ProcessorMode.RULES));
        assertTrue(modes.contains(ProcessorMode.SPRING_AI));
        assertTrue(modes.contains(ProcessorMode.LANGCHAIN4J));
    }

    @Test
    void aiProcessorsAreUnavailableWithoutKey() {
        processors.stream()
                .filter(p -> p.mode() != ProcessorMode.RULES)
                .forEach(p -> assertFalse(p.available(), p.mode() + " should be unavailable"));
    }

    @Test
    void resolverFallsBackToRules() {
        assertEquals(ProcessorMode.RULES, resolver.resolve().mode());
    }
}
