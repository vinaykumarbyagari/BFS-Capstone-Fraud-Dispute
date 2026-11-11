package com.capstone.fraud.web;

import com.capstone.fraud.domain.Channel;
import com.capstone.fraud.domain.DisputeType;
import com.capstone.fraud.domain.FraudCase;
import com.capstone.fraud.dto.CaseIntakeRequest;
import com.capstone.fraud.repo.*;
import com.capstone.fraud.service.CaseMemoryService;
import com.capstone.fraud.service.CaseService;
import com.capstone.fraud.service.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cases")
public class CaseController {

    private final CaseService caseService;
    private final EvidenceRecordRepository evidenceRepo;
    private final InvestigationStepRepository stepRepo;
    private final RecommendationRepository recommendationRepo;
    private final AuditEventRepository auditRepo;
    private final CaseMemoryService memory;

    public CaseController(CaseService caseService, EvidenceRecordRepository evidenceRepo,
                          InvestigationStepRepository stepRepo, RecommendationRepository recommendationRepo,
                          AuditEventRepository auditRepo, CaseMemoryService memory) {
        this.caseService = caseService;
        this.evidenceRepo = evidenceRepo;
        this.stepRepo = stepRepo;
        this.recommendationRepo = recommendationRepo;
        this.auditRepo = auditRepo;
        this.memory = memory;
    }

    @GetMapping("/new")
    public String newCaseForm(Model model) {
        model.addAttribute("form", new CaseIntakeRequest());
        model.addAttribute("channels", Channel.values());
        model.addAttribute("disputeTypes", DisputeType.values());
        return "cases/new";
    }

    @PostMapping("/new")
    public String createCase(@Valid @ModelAttribute("form") CaseIntakeRequest form,
                             BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("channels", Channel.values());
            model.addAttribute("disputeTypes", DisputeType.values());
            return "cases/new";
        }
        FraudCase c = caseService.intake(form, SecurityUtils.currentUser());
        if (form.isInvestigateNow()) {
            caseService.investigate(c.getId(), SecurityUtils.currentUser());
        }
        return "redirect:/cases/" + c.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        FraudCase c = caseService.getCase(id);
        model.addAttribute("c", c);
        model.addAttribute("evidence", evidenceRepo.findByCaseIdOrderBySeq(id));
        model.addAttribute("steps", stepRepo.findByCaseIdOrderBySeq(id));
        model.addAttribute("recommendations", recommendationRepo.findByCaseIdOrderByCreatedAtDesc(id));
        model.addAttribute("reviews", caseService.reviewsFor(id));
        model.addAttribute("audit", auditRepo.findByCaseIdOrderByCreatedAt(id));
        model.addAttribute("memory", memory.history(id));
        return "cases/detail";
    }

    @PostMapping("/{id}/investigate")
    public String investigate(@PathVariable String id, RedirectAttributes ra) {
        try {
            caseService.investigate(id, SecurityUtils.currentUser());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }
}
