package com.capstone.fraud.dto;

import com.capstone.fraud.domain.CaseRoute;
import com.capstone.fraud.domain.Priority;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * The framework-neutral recommendation contract. Every processor mode
 * (rules, Spring AI, LangChain4j) produces this shape, and in AI modes
 * the raw model output is validated against
 * schema/recommendation.schema.json before it is accepted.
 */
@Getter
@Setter
public class RecommendationOutput {
    private CaseRoute route;
    private Priority priority = Priority.MEDIUM;
    private double confidence;
    private String summary;
    private List<String> reasons = new ArrayList<>();
    private List<PolicyCitation> policyCitations = new ArrayList<>();
    private List<EvidenceRequestItem> evidenceRequests = new ArrayList<>();
    private boolean abstained;
    private String abstainReason;
}
