package com.capstone.fraud.ai;

import com.capstone.fraud.dto.RecommendationOutput;

/**
 * Result of a processor's analysis step.
 *
 * @param output      parsed contract output
 * @param rawOutput   raw model text (null for rules mode)
 * @param schemaValid whether the raw output passed schema validation
 */
public record AnalysisResult(RecommendationOutput output, String rawOutput, boolean schemaValid) {
}
