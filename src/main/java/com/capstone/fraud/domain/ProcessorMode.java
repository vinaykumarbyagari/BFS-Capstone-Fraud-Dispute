package com.capstone.fraud.domain;

public enum ProcessorMode {
    RULES,
    SPRING_AI,
    LANGCHAIN4J,
    /** AI path was selected but the model call failed; rules engine answered. */
    RULES_FALLBACK
}
