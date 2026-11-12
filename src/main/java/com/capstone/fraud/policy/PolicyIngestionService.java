package com.capstone.fraud.policy;

import com.capstone.fraud.domain.PolicyChunk;
import com.capstone.fraud.repo.PolicyChunkRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ingests policy markdown documents from classpath:policies/ into
 * policy_chunk rows. Chunks are split on "## " section headings so each
 * chunk carries exact citation metadata (policyId, version, section,
 * status, effectiveDate). Ingestion is idempotent per document: only
 * policyIds not yet present are loaded, so new manifest entries are
 * picked up on restart without duplicating existing rows.
 */
@Slf4j
@Service
public class PolicyIngestionService {

    private final PolicyChunkRepository chunkRepo;

    public PolicyIngestionService(PolicyChunkRepository chunkRepo) {
        this.chunkRepo = chunkRepo;
    }

    @PostConstruct
    void ingest() {
        List<Map<String, Object>> manifest = readManifest();
        int total = 0;
        int skipped = 0;
        for (Map<String, Object> doc : manifest) {
            String policyId = String.valueOf(doc.get("policyId"));
            if (chunkRepo.countByPolicyId(policyId) > 0) {
                skipped++;
                continue;
            }
            total += ingestDocument(doc);
        }
        log.info("Policy ingestion: {} chunks ingested, {} documents already present", total, skipped);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readManifest() {
        try (InputStream in = new ClassPathResource("policies/policies.yml").getInputStream()) {
            Map<String, Object> yaml = new Yaml().load(in);
            return (List<Map<String, Object>>) yaml.get("policies");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read policies/policies.yml", e);
        }
    }

    private int ingestDocument(Map<String, Object> doc) {
        String policyId = String.valueOf(doc.get("policyId"));
        String version = String.valueOf(doc.get("version"));
        String title = String.valueOf(doc.get("title"));
        String file = String.valueOf(doc.get("file"));
        String status = String.valueOf(doc.getOrDefault("status", "active")).toUpperCase();
        String effectiveDate = String.valueOf(doc.getOrDefault("effectiveDate", ""));

        String markdown;
        try (InputStream in = new ClassPathResource("policies/" + file).getInputStream()) {
            markdown = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read policies/" + file, e);
        }

        List<PolicyChunk> chunks = new ArrayList<>();
        // split on "## " section headings, keeping the heading as chunk section
        String[] parts = markdown.split("(?m)^(?=## )");
        int idx = 0;
        for (String part : parts) {
            String text = part.strip();
            if (text.isEmpty()) continue;
            PolicyChunk c = new PolicyChunk();
            c.setPolicyId(policyId);
            c.setPolicyVersion(version);
            c.setPolicyTitle(title);
            c.setSection(extractSection(text, idx));
            c.setChunkIndex(idx++);
            c.setStatus(status);
            c.setEffectiveDate(effectiveDate);
            c.setText(text);
            chunks.add(c);
        }
        chunkRepo.saveAll(chunks);
        return chunks.size();
    }

    private String extractSection(String text, int idx) {
        if (text.startsWith("## ")) {
            int nl = text.indexOf('\n');
            String heading = nl > 0 ? text.substring(3, nl) : text.substring(3);
            return heading.trim();
        }
        return idx == 0 ? "Preamble" : "Section " + idx;
    }
}
