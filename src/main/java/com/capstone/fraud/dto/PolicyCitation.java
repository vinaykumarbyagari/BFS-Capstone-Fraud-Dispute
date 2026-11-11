package com.capstone.fraud.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PolicyCitation {
    private String policyId;
    private String policyVersion;
    private String section;
    private String title;
    private double score;
    private String snippet;

    public static PolicyCitation of(String policyId, String version, String section,
                                    String title, double score, String snippet) {
        PolicyCitation c = new PolicyCitation();
        c.setPolicyId(policyId);
        c.setPolicyVersion(version);
        c.setSection(section);
        c.setTitle(title);
        c.setScore(score);
        c.setSnippet(snippet);
        return c;
    }
}
