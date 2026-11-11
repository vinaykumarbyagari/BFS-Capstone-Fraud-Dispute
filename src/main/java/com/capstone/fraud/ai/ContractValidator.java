package com.capstone.fraud.ai;

import com.capstone.fraud.dto.RecommendationOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Validates raw model output against the published recommendation
 * contract (schema/recommendation.schema.json) and only then maps it
 * to {@link RecommendationOutput}. Model output that fails validation
 * never reaches the application — the run falls back to rules mode.
 */
@Component
public class ContractValidator {

    private final Schema schema;
    private final ObjectMapper objectMapper;

    public ContractValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try (InputStream in = new ClassPathResource("schema/recommendation.schema.json").getInputStream()) {
            this.schema = SchemaLoader.load(new JSONObject(new JSONTokener(in)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load recommendation JSON schema", e);
        }
    }

    /** @return validation error messages; empty means the document conforms */
    public List<String> validate(String json) {
        try {
            schema.validate(new JSONObject(json));
            return List.of();
        } catch (ValidationException e) {
            return e.getAllMessages();
        } catch (Exception e) {
            return List.of("unparseable JSON: " + e.getMessage());
        }
    }

    public RecommendationOutput parse(String json) {
        try {
            return objectMapper.readValue(json, RecommendationOutput.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot map model output to contract: " + e.getMessage(), e);
        }
    }
}
