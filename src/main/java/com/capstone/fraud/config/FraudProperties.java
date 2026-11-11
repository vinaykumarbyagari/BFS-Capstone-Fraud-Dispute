package com.capstone.fraud.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fraud")
public class FraudProperties {

    private final Processor processor = new Processor();
    private final Anthropic anthropic = new Anthropic();
    private final Evidence evidence = new Evidence();
    private final Retrieval retrieval = new Retrieval();
    private final Rules rules = new Rules();
    private final Security security = new Security();

    @Getter
    @Setter
    public static class Processor {
        /** auto | rules | springai | langchain4j */
        private String mode = "auto";
    }

    @Getter
    @Setter
    public static class Anthropic {
        private String apiKey = "";
        private String model = "claude-sonnet-4-5";
        /** gateway/proxy root URL; empty = default api.anthropic.com */
        private String baseUrl = "";
    }

    @Getter
    @Setter
    public static class Evidence {
        /** bounded tool budget per investigation run */
        private int maxToolCalls = 8;
    }

    @Getter
    @Setter
    public static class Retrieval {
        private int topK = 5;
        /** below this score the retriever declares policy context insufficient */
        private double minScore = 0.10;
    }

    @Getter
    @Setter
    public static class Rules {
        private double escalateAmount = 10_000;
        private double manualReviewAmount = 5_000;
        private double highAmount = 1_000;
        private double fraudScoreEscalate = 0.65;
        private double fraudScoreFraudLikely = 0.45;
        private double fraudScoreLegitLikely = 0.25;
        private double merchantRiskHigh = 0.75;
        private double merchantRiskMedium = 0.45;
    }

    @Getter
    @Setter
    public static class Security {
        private String analystPassword = "analyst123";
        private String reviewerPassword = "reviewer123";
    }
}
