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

    private Optional<Map<String, Object>> find(List<Map<String, Object>> rows, String key, Object value) {
        if (value == null) return Optional.empty();
        return rows.stream().filter(r -> value.equals(r.get(key))).findFirst();
    }
}
