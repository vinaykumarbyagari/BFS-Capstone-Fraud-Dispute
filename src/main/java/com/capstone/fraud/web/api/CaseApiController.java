package com.capstone.fraud.web.api;

import com.capstone.fraud.domain.FraudCase;
import com.capstone.fraud.dto.CaseIntakeRequest;
import com.capstone.fraud.dto.ProcessingOutcome;
import com.capstone.fraud.dto.ReviewForm;
import com.capstone.fraud.repo.*;
import com.capstone.fraud.service.CaseService;
import com.capstone.fraud.service.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * JSON API for the same intake -> investigate -> review pipeline.
 * Session-authenticated; CSRF-exempt per SecurityConfig.
 */
@RestController
@RequestMapping("/api/cases")
public class CaseApiController {

    private final CaseService caseService;
    private final EvidenceRecordRepository evidenceRepo;
    private final InvestigationStepRepository stepRepo;
    private final RecommendationRepository recommendationRepo;
    private final AuditEventRepository auditRepo;

    public CaseApiController(CaseService caseService, EvidenceRecordRepository evidenceRepo,
                             InvestigationStepRepository stepRepo,
                             RecommendationRepository recommendationRepo, AuditEventRepository auditRepo) {
        this.caseService = caseService;
        this.evidenceRepo = evidenceRepo;
        this.stepRepo = stepRepo;
        this.recommendationRepo = recommendationRepo;
        this.auditRepo = auditRepo;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CaseIntakeRequest req) {
        FraudCase c = caseService.intake(req, SecurityUtils.currentUser());
        Map<String, Object> body = caseSummary(c);
        if (req.isInvestigateNow()) {
            ProcessingOutcome o = caseService.investigate(c.getId(), SecurityUtils.currentUser());
            body.put("status", caseService.getCase(c.getId()).getStatus());
            body.put("recommendation", recommendationMap(o.getRecommendation()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/investigate")
    public Map<String, Object> investigate(@PathVariable String id) {
        ProcessingOutcome o = caseService.investigate(id, SecurityUtils.currentUser());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", o.getRunId());
        body.put("recommendation", recommendationMap(o.getRecommendation()));
        body.put("status", caseService.getCase(id).getStatus());
        return body;
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        Map<String, Object> body = caseSummary(caseService.getCase(id));
        recommendationRepo.findFirstByCaseIdOrderByCreatedAtDesc(id)
                .ifPresent(r -> body.put("recommendation", recommendationMap(r)));
        body.put("reviews", caseService.reviewsFor(id));
        return body;
    }

    @GetMapping("/{id}/trace")
    public Object trace(@PathVariable String id) {
        caseService.getCase(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("evidence", evidenceRepo.findByCaseIdOrderBySeq(id));
        body.put("steps", stepRepo.findByCaseIdOrderBySeq(id));
        return body;
    }

    @GetMapping("/{id}/audit")
    public Object audit(@PathVariable String id) {
        caseService.getCase(id);
        return auditRepo.findByCaseIdOrderByCreatedAt(id);
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<?> review(@PathVariable String id, @Valid @RequestBody ReviewForm form) {
        var d = caseService.recordReview(id, form, SecurityUtils.currentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(d);
    }

    private Map<String, Object> caseSummary(FraudCase c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("caseNumber", c.getCaseNumber());
        m.put("customerId", c.getCustomerId());
        m.put("status", c.getStatus());
        m.put("amount", c.getAmount());
        m.put("currency", c.getCurrency());
        m.put("disputeType", c.getDisputeType());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    private Map<String, Object> recommendationMap(com.capstone.fraud.domain.Recommendation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("mode", r.getMode());
        m.put("route", r.getRoute());
        m.put("priority", r.getPriority());
        m.put("confidence", r.getConfidence());
        m.put("summary", r.getSummary());
        m.put("reasons", r.getReasonsJson());
        m.put("policyCitations", r.getCitationsJson());
        m.put("evidenceRequests", r.getEvidenceRequestsJson());
        m.put("abstained", r.isAbstained());
        m.put("abstainReason", r.getAbstainReason());
        m.put("schemaValid", r.isSchemaValid());
        m.put("modelName", r.getModelName());
        return m;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> dataConflict(
            org.springframework.dao.DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "conflicting case data (possible duplicate)"));
    }
}
