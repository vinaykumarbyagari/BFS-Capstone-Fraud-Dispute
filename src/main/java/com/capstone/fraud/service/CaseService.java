package com.capstone.fraud.service;

import com.capstone.fraud.ai.FraudCaseProcessor;
import com.capstone.fraud.ai.FraudCaseProcessorResolver;
import com.capstone.fraud.domain.*;
import com.capstone.fraud.dto.CaseIntakeRequest;
import com.capstone.fraud.dto.ProcessingOutcome;
import com.capstone.fraud.dto.ReviewForm;
import com.capstone.fraud.repo.FraudCaseRepository;
import com.capstone.fraud.repo.RecommendationRepository;
import com.capstone.fraud.repo.ReviewDecisionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class CaseService {

    private final FraudCaseRepository caseRepo;
    private final RecommendationRepository recommendationRepo;
    private final ReviewDecisionRepository reviewRepo;
    private final FraudCaseProcessorResolver processorResolver;
    private final AuditService audit;
    private final CaseMemoryService memory;
    private final AtomicLong caseCounter = new AtomicLong(1000);

    public CaseService(FraudCaseRepository caseRepo, RecommendationRepository recommendationRepo,
                       ReviewDecisionRepository reviewRepo, FraudCaseProcessorResolver processorResolver,
                       AuditService audit, CaseMemoryService memory) {
        this.caseRepo = caseRepo;
        this.recommendationRepo = recommendationRepo;
        this.reviewRepo = reviewRepo;
        this.processorResolver = processorResolver;
        this.audit = audit;
        this.memory = memory;
    }

    @Transactional
    public FraudCase intake(CaseIntakeRequest req, String actor) {
        FraudCase c = new FraudCase();
        c.setCaseNumber("FC-" + caseCounter.incrementAndGet());
        c.setCustomerId(req.getCustomerId());
        c.setCardLast4(req.getCardLast4());
        c.setTransactionRef(req.getTransactionRef());
        c.setMerchantName(req.getMerchantName());
        c.setAmount(req.getAmount());
        c.setCurrency(req.getCurrency());
        c.setChannel(req.getChannel());
        c.setDisputeType(req.getDisputeType());
        c.setDescription(req.getDescription());
        c.setCreatedBy(actor);
        c.setStatus(CaseStatus.INTAKE);
        caseRepo.save(c);

        memory.append(c.getId(), CaseMessage.ROLE_USER,
                "Dispute intake: " + safe(req.getDisputeType()) + " on "
                    + safe(req.getMerchantName()) + " amount " + req.getAmount() + " " + req.getCurrency()
                    + (req.getDescription() != null ? ". Customer statement: " + req.getDescription() : ""));
        audit.record(c.getId(), actor, "CASE_CREATED",
                Map.of("caseNumber", c.getCaseNumber(), "disputeType", String.valueOf(req.getDisputeType()),
                        "amount", String.valueOf(req.getAmount()), "channel", String.valueOf(req.getChannel())));
        return c;
    }

    public ProcessingOutcome investigate(String caseId, String actor) {
        FraudCase c = getCase(caseId);
        if (c.getStatus() == CaseStatus.CLOSED) {
            throw new IllegalStateException("Case " + c.getCaseNumber() + " is closed");
        }
        c.setStatus(CaseStatus.INVESTIGATING);
        caseRepo.save(c);
        audit.record(caseId, actor, "INVESTIGATION_STARTED", Map.of("caseNumber", c.getCaseNumber()));

        FraudCaseProcessor processor = processorResolver.resolve();
        ProcessingOutcome outcome = processor.investigate(c);

        CaseRoute route = outcome.getOutput().getRoute();
        c.setStatus(route == CaseRoute.REQUEST_EVIDENCE
                ? CaseStatus.EVIDENCE_REQUESTED
                : CaseStatus.AWAITING_REVIEW);
        caseRepo.save(c);
        audit.record(caseId, actor, "INVESTIGATION_COMPLETED", Map.of(
                "caseNumber", c.getCaseNumber(),
                "mode", outcome.getRecommendation().getMode().name(),
                "route", route.name(),
                "abstained", outcome.getOutput().isAbstained(),
                "runId", outcome.getRunId()));
        return outcome;
    }

    @Transactional
    public ReviewDecision recordReview(String caseId, ReviewForm form, String reviewer) {
        FraudCase c = getCase(caseId);
        ReviewDecision d = new ReviewDecision();
        d.setCaseId(caseId);
        d.setReviewerName(reviewer);
        d.setDisposition(form.getDisposition());
        d.setRationale(form.getRationale());
        recommendationRepo.findFirstByCaseIdOrderByCreatedAtDesc(caseId)
                .ifPresent(r -> d.setRecommendationId(r.getId()));
        reviewRepo.save(d);

        c.setStatus(form.getDisposition() == Disposition.MORE_EVIDENCE_REQUESTED
                ? CaseStatus.EVIDENCE_REQUESTED
                : CaseStatus.CLOSED);
        caseRepo.save(c);

        memory.append(caseId, CaseMessage.ROLE_ASSISTANT,
                "Human review by " + reviewer + ": " + form.getDisposition()
                        + " — " + form.getRationale());
        audit.record(caseId, reviewer, "REVIEW_RECORDED", Map.of(
                "caseNumber", c.getCaseNumber(),
                "disposition", form.getDisposition().name(),
                "reviewer", reviewer));
        return d;
    }

    public FraudCase getCase(String id) {
        return caseRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No case with id " + id));
    }

    public List<FraudCase> allCases() {
        return caseRepo.findAllByOrderByCreatedAtDesc();
    }

    public List<ReviewDecision> reviewsFor(String caseId) {
        return reviewRepo.findByCaseIdOrderByCreatedAtDesc(caseId);
    }

    public String activeMode() {
        return processorResolver.resolve().mode().name();
    }

    private static String safe(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
