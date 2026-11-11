package com.capstone.fraud.web;

import com.capstone.fraud.domain.Disposition;
import com.capstone.fraud.domain.FraudCase;
import com.capstone.fraud.dto.ReviewForm;
import com.capstone.fraud.repo.RecommendationRepository;
import com.capstone.fraud.service.CaseService;
import com.capstone.fraud.service.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/cases/{id}/review")
public class ReviewController {

    private final CaseService caseService;
    private final RecommendationRepository recommendationRepo;

    public ReviewController(CaseService caseService, RecommendationRepository recommendationRepo) {
        this.caseService = caseService;
        this.recommendationRepo = recommendationRepo;
    }

    @GetMapping
    public String reviewForm(@PathVariable String id, Model model) {
        FraudCase c = caseService.getCase(id);
        model.addAttribute("c", c);
        model.addAttribute("form", new ReviewForm());
        model.addAttribute("dispositions", Disposition.values());
        model.addAttribute("recommendation",
                recommendationRepo.findFirstByCaseIdOrderByCreatedAtDesc(id).orElse(null));
        model.addAttribute("reviews", caseService.reviewsFor(id));
        return "cases/review";
    }

    @PostMapping
    public String submitReview(@PathVariable String id,
                               @Valid @ModelAttribute("form") ReviewForm form,
                               BindingResult binding, Model model) {
        FraudCase c = caseService.getCase(id);
        if (binding.hasErrors()) {
            model.addAttribute("c", c);
            model.addAttribute("dispositions", Disposition.values());
            model.addAttribute("recommendation",
                    recommendationRepo.findFirstByCaseIdOrderByCreatedAtDesc(id).orElse(null));
            model.addAttribute("reviews", caseService.reviewsFor(id));
            return "cases/review";
        }
        caseService.recordReview(id, form, SecurityUtils.currentUser());
        return "redirect:/cases/" + id;
    }
}
