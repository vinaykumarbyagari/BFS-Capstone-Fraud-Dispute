package com.capstone.fraud.policy;

import com.capstone.fraud.domain.PolicyChunk;
import lombok.Getter;

@Getter
public class RetrievedPassage {
    private final PolicyChunk chunk;
    private final double score;

    public RetrievedPassage(PolicyChunk chunk, double score) {
        this.chunk = chunk;
        this.score = score;
    }

    public String citationKey() {
        PolicyChunk c = chunk;
        return c.getPolicyId() + " v" + c.getPolicyVersion() + " §" + c.getSection();
    }

    public String snippet(int max) {
        String t = chunk.getText().replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
