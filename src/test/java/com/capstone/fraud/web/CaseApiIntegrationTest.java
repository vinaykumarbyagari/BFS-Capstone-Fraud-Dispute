package com.capstone.fraud.web;

import com.capstone.fraud.domain.Disposition;
import com.capstone.fraud.repo.AuditEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end flow through the REST API: intake -> investigate -> review,
 * verifying the append-only audit trail records each step.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CaseApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AuditEventRepository auditRepo;

    private static final String INTAKE = """
            {"customerId":"CUST-101","cardLast4":"4412","transactionRef":"TXN-5001",
             "merchantName":"SKYLINE ELECTRONICS","amount":1240.00,"currency":"USD",
             "channel":"CARD_NOT_PRESENT","disputeType":"UNAUTHORIZED_TRANSACTION",
             "description":"customer denies this purchase","investigateNow":true}
            """;

    @Test
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void fullFlowIntakeInvestigateReview() throws Exception {
        // intake + investigate in one call
        MvcResult created = mvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON).content(INTAKE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseNumber").exists())
                .andExpect(jsonPath("$.recommendation.route").value("ESCALATE_FRAUD_OPS"))
                .andReturn();
        String caseId = mapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        // trace is observable
        mvc.perform(get("/api/cases/{id}/trace", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence.length()").value(5))
                .andExpect(jsonPath("$.steps").isArray());

        // human review records disposition
        String review = """
                {"disposition":"FRAUD_CONFIRMED",
                 "rationale":"Device and geography signals confirm unauthorized use."}
                """;
        mvc.perform(post("/api/cases/{id}/review", caseId)
                        .contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disposition").value("FRAUD_CONFIRMED"));

        JsonNode detail = mapper.readTree(mvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("CLOSED", detail.get("status").asText());

        // audit trail captured each lifecycle event
        var events = auditRepo.findByCaseIdOrderByCreatedAt(caseId).stream()
                .map(e -> e.getEventType()).toList();
        assertTrue(events.contains("CASE_CREATED"));
        assertTrue(events.contains("INVESTIGATION_STARTED"));
        assertTrue(events.contains("INVESTIGATION_COMPLETED"));
        assertTrue(events.contains("REVIEW_RECORDED"));
    }

    @Test
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void reviewRequiresRationale() throws Exception {
        MvcResult created = mvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON).content(INTAKE))
                .andExpect(status().isCreated()).andReturn();
        String caseId = mapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(post("/api/cases/{id}/review", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"LEGITIMATE_TRANSACTION\",\"rationale\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void unknownCase404() throws Exception {
        mvc.perform(get("/api/cases/{id}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
