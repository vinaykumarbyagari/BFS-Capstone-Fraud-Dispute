package com.capstone.fraud.web;

import com.capstone.fraud.domain.PolicyChunk;
import com.capstone.fraud.repo.PolicyChunkRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/policies")
public class PolicyController {

    private final PolicyChunkRepository chunkRepo;

    public PolicyController(PolicyChunkRepository chunkRepo) {
        this.chunkRepo = chunkRepo;
    }

    @GetMapping
    public String policies(Model model) {
        Map<String, List<PolicyChunk>> byPolicy = new LinkedHashMap<>();
        for (PolicyChunk c : chunkRepo.findAll()) {
            byPolicy.computeIfAbsent(c.getPolicyId(), k -> new java.util.ArrayList<>()).add(c);
        }
        model.addAttribute("byPolicy", byPolicy);
        return "policies";
    }
}
