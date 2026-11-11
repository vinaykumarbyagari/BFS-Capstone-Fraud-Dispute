package com.capstone.fraud.web;

import com.capstone.fraud.repo.AuditEventRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/audit")
public class AuditController {

    private final AuditEventRepository auditRepo;

    public AuditController(AuditEventRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @GetMapping
    public String audit(Model model) {
        model.addAttribute("events", auditRepo.findTop100ByOrderByCreatedAtDesc());
        return "audit";
    }
}
