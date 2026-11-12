package com.capstone.fraud.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads deterministic synthetic fixtures from classpath:fixtures/.
 * All data is fabricated for training/demonstration — no real
 * customer, card or transaction data is present.
 */
@Slf4j
@Service
public class FixtureDataService {

    private final ObjectMapper objectMapper;

    @Getter
    private List<Map<String, Object>> transactions = List.of();
    @Getter
    private List<Map<String, Object>> customers = List.of();
    @Getter
    private List<Map<String, Object>> deviceProfiles = List.of();
    @Getter
    private List<Map<String, Object>> merchants = List.of();
    @Getter
    private List<Map<String, Object>> priorCases = List.of();
    @Getter
    private List<Map<String, Object>> activity = List.of();
    @Getter
    private List<Map<String, Object>> screening = List.of();

    public FixtureDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        transactions = read("fixtures/transactions.json");
        customers = read("fixtures/customers.json");
        deviceProfiles = read("fixtures/devices.json");
        merchants = read("fixtures/merchants.json");
        priorCases = read("fixtures/prior_cases.json");
        activity = read("fixtures/activity.json");
        screening = read("fixtures/screening.json");
        log.info("Loaded synthetic fixtures: {} transactions, {} customers, {} merchants",
                transactions.size(), customers.size(), merchants.size());
    }

    private List<Map<String, Object>> read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load fixture " + path, e);
        }
    }

    public Optional<Map<String, Object>> transaction(String ref) {
        return find(transactions, "ref", ref);
    }

    public Optional<Map<String, Object>> customer(String customerId) {
        return find(customers, "customerId", customerId);
    }

    public Optional<Map<String, Object>> deviceProfile(String customerId) {
        return find(deviceProfiles, "customerId", customerId);
    }

    public Optional<Map<String, Object>> merchant(String name) {
        if (name == null) return Optional.empty();
        return merchants.stream()
                .filter(m -> name.equalsIgnoreCase(String.valueOf(m.get("name"))))
                .findFirst();
    }

    public List<Map<String, Object>> priorCasesFor(String customerId) {
        return priorCases.stream()
                .filter(p -> customerId != null && customerId.equals(p.get("customerId")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> recentActivityFor(String customerId) {
        return activity.stream()
                .filter(a -> customerId != null && customerId.equals(a.get("customerId")))
                .findFirst()
                .map(a -> (List<Map<String, Object>>) a.getOrDefault("recentTransactions", List.of()))
                .orElse(List.of());
    }

    public List<Map<String, Object>> screeningHitsFor(String customerId, String merchantName) {
        return screening.stream()
                .filter(s -> (customerId != null && customerId.equals(s.get("key")))
                        || (merchantName != null && merchantName.equalsIgnoreCase(String.valueOf(s.get("key")))))
                .toList();
    }

    private Optional<Map<String, Object>> find(List<Map<String, Object>> rows, String key, Object value) {
        if (value == null) return Optional.empty();
        return rows.stream().filter(r -> value.equals(r.get(key))).findFirst();
    }
}
