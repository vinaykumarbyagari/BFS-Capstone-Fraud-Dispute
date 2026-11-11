package com.capstone.fraud.tools;

import com.capstone.fraud.domain.FraudCase;

import java.util.Map;

/**
 * A read-only evidence tool. Tools may only read synthetic fixture data;
 * they can never mutate accounts, block cards, move funds or contact
 * customers. Execution goes through {@link EvidenceToolExecutor}, which
 * records every invocation in the observable trace.
 */
public interface EvidenceTool {

    String name();

    /** One-line description shown to the planner model. */
    String description();

    /** argument name -> description, for the tool catalog in prompts */
    Map<String, String> argumentSpec();

    ToolResult execute(Map<String, Object> args, FraudCase fraudCase);
}
