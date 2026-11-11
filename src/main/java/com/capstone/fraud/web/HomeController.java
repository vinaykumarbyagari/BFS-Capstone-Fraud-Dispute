package com.capstone.fraud.web;

import com.capstone.fraud.domain.CaseStatus;
import com.capstone.fraud.repo.FraudCaseRepository;
import com.capstone.fraud.service.CaseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final CaseService caseService;
    private final FraudCaseRepository caseRepo;

    public HomeController(CaseService caseService, FraudCaseRepository caseRepo) {
        this.caseService = caseService;
        this.caseRepo = caseRepo;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("cases", caseService.allCases());
        model.addAttribute("mode", caseService.activeMode());
        model.addAttribute("awaiting", caseRepo.countByStatus(CaseStatus.AWAITING_REVIEW));
        model.addAttribute("intake", caseRepo.countByStatus(CaseStatus.INTAKE));
        model.addAttribute("closed", caseRepo.countByStatus(CaseStatus.CLOSED));
        model.addAttribute("evidenceReq", caseRepo.countByStatus(CaseStatus.EVIDENCE_REQUESTED));
        return "index";
    }
}
