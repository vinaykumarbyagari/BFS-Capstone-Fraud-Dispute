package com.capstone.fraud.tools;

import lombok.Getter;

import java.util.Map;

@Getter
public class ToolResult {

    public static final String SUCCESS = "SUCCESS";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String ERROR = "ERROR";
    public static final String REJECTED = "REJECTED";

    private final String outcome;
    private final Map<String, Object> payload;
    private final String summary;

    private ToolResult(String outcome, Map<String, Object> payload, String summary) {
        this.outcome = outcome;
        this.payload = payload;
        this.summary = summary;
    }

    public static ToolResult success(Map<String, Object> payload, String summary) {
        return new ToolResult(SUCCESS, payload, summary);
    }

    public static ToolResult notFound(String summary) {
        return new ToolResult(NOT_FOUND, Map.of("found", false), summary);
    }

    public static ToolResult error(String summary) {
        return new ToolResult(ERROR, Map.of(), summary);
    }

    public static ToolResult rejected(String summary) {
        return new ToolResult(REJECTED, Map.of(), summary);
    }
}
