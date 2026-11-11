package com.capstone.fraud.policy;

import lombok.Getter;

import java.util.List;

@Getter
public class RetrievalResult {
    private final List<RetrievedPassage> passages;
    private final boolean sufficient;
    private final double maxScore;

    public RetrievalResult(List<RetrievedPassage> passages, boolean sufficient, double maxScore) {
        this.passages = passages;
        this.sufficient = sufficient;
        this.maxScore = maxScore;
    }
}
