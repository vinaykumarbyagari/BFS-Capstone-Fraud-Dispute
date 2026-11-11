package com.capstone.fraud.dto;

import com.capstone.fraud.domain.Recommendation;
import lombok.Getter;

@Getter
public class ProcessingOutcome {
    private final String runId;
    private final Recommendation recommendation;
    private final RecommendationOutput output;

    public ProcessingOutcome(String runId, Recommendation recommendation, RecommendationOutput output) {
        this.runId = runId;
        this.recommendation = recommendation;
        this.output = output;
    }
}
