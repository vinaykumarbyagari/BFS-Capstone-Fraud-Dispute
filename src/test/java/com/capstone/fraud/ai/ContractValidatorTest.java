package com.capstone.fraud.ai;

import com.capstone.fraud.dto.RecommendationOutput;
import com.capstone.fraud.domain.CaseRoute;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContractValidatorTest {

    private final ContractValidator validator = new ContractValidator(new ObjectMapper());

    private static final String VALID = """
            {"route":"MANUAL_REVIEW","priority":"MEDIUM","confidence":0.4,
             "summary":"ambiguous","reasons":["r1"],"policyCitations":[],
             "evidenceRequests":[],"abstained":false,"abstainReason":null}
            """;

    @Test
    void acceptsValidDocument() {
        assertTrue(validator.validate(VALID).isEmpty());
        RecommendationOutput out = validator.parse(VALID);
        assertEquals(CaseRoute.MANUAL_REVIEW, out.getRoute());
        assertEquals(0.4, out.getConfidence());
    }

    @Test
    void rejectsMissingRequiredField() {
        String invalid = VALID.replace("\"route\":\"MANUAL_REVIEW\",", "");
        assertFalse(validator.validate(invalid).isEmpty());
    }

    @Test
    void rejectsUnknownRoute() {
        String invalid = VALID.replace("MANUAL_REVIEW", "AUTO_REFUND");
        assertFalse(validator.validate(invalid).isEmpty());
    }

    @Test
    void rejectsConfidenceOutOfRange() {
        String invalid = VALID.replace("0.4", "1.7");
        assertFalse(validator.validate(invalid).isEmpty());
    }

    @Test
    void rejectsUndeclaredProperties() {
        String invalid = VALID.replace("\"abstained\":false",
                "\"abstained\":false,\"cardBlocked\":true");
        assertFalse(validator.validate(invalid).isEmpty());
    }

    @Test
    void rejectsNonJson() {
        assertFalse(validator.validate("not json").isEmpty());
    }
}
