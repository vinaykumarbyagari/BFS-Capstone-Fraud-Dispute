package com.capstone.fraud.ai;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.*;
import com.capstone.fraud.dto.EvidenceRequestItem;
import com.capstone.fraud.dto.PolicyCitation;
import com.capstone.fraud.dto.RecommendationOutput;
import com.capstone.fraud.policy.RetrievalResult;
import com.capstone.fraud.policy.RetrievedPassage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Deterministic rules analyzer — the safe fallback for every mode.
 * Computes a transparent fraud-signal score from collected evidence and
 * maps it to an advisory route under thresholds externalized in
 * {@code fraud.rules.*}. Every fired signal is recorded as a reason,
 * so the recommendation is explainable without exposing chain-of-thought.
 */
@Component
public class RulesAnalyzer {

    private final FraudProperties.Rules rules;
    private final ObjectMapper objectMapper;

    public RulesAnalyzer(FraudProperties properties, ObjectMapper objectMapper) {
        this.rules = properties.getRules();
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public RecommendationOutput analyze(FraudCase c, List<EvidenceRecord> evidence, RetrievalResult retrieval) {
        EvidenceView ev = EvidenceView.of(evidence, objectMapper);
        RecommendationOutput out = new RecommendationOutput();
        List<String> reasons = out.getReasons();

        // ---- missing evidence drives a request, not a guess ----
        if (!ev.found("transaction_lookup")) {
            out.getEvidenceRequests().add(new EvidenceRequestItem(
                    "transaction_record",
                    "Disputed transaction " + safe(c.getTransactionRef()) + " was not found in the transaction system"));
        }
        if (!ev.found("customer_profile")) {
            out.getEvidenceRequests().add(new EvidenceRequestItem(
                    "customer_profile",
                    "Customer " + safe(c.getCustomerId()) + " was not found"));
        }
        if (!out.getEvidenceRequests().isEmpty()) {
            out.setRoute(CaseRoute.REQUEST_EVIDENCE);
            out.setPriority(Priority.MEDIUM);
            out.setConfidence(0.9);
            out.setSummary("Core evidence is missing; requesting it before recommending a route.");
            reasons.add("required evidence unavailable from evidence tools");
            attachCitations(out, retrieval);
            return out;
        }

        // ---- fraud signal scoring ----
        double score = 0;
        Map<String, Object> customer = ev.get("customer_profile");
        Map<String, Object> merchant = ev.get("merchant_risk");
        Map<String, Object> devices = ev.get("device_login_history");
        Map<String, Object> prior = ev.get("prior_case_history");
        Map<String, Object> tx = ev.get("transaction_lookup");

        double amount = c.getAmount() != null ? c.getAmount().doubleValue() : number(tx.get("amount"));

        double merchantRisk = number(merchant.get("riskScore"));
        if (merchantRisk >= rules.getMerchantRiskHigh()) {
            score += 0.35;
            reasons.add(String.format("merchant risk score %.2f >= high threshold %.2f (+0.35)",
                    merchantRisk, rules.getMerchantRiskHigh()));
        } else if (merchantRisk >= rules.getMerchantRiskMedium()) {
            score += 0.15;
            reasons.add(String.format("merchant risk score %.2f >= medium threshold %.2f (+0.15)",
                    merchantRisk, rules.getMerchantRiskMedium()));
        }
        int priorReports = (int) number(merchant.get("priorFraudReports"));
        if (priorReports >= 10) {
            score += 0.10;
            reasons.add("merchant has " + priorReports + " prior fraud reports (+0.10)");
        }

        if (!devices.isEmpty()) {
            List<Map<String, Object>> logins = (List<Map<String, Object>>) devices.getOrDefault("recentLogins", List.of());
            String homeCountry = String.valueOf(customer.getOrDefault("country", ""));
            boolean newDevice = logins.stream().anyMatch(l -> !Boolean.TRUE.equals(l.get("trusted"))
                    && Boolean.TRUE.equals(l.get("success")));
            boolean geoMismatch = logins.stream().anyMatch(l ->
                    !homeCountry.equalsIgnoreCase(String.valueOf(l.get("country")))
                            && Boolean.TRUE.equals(l.get("success")));
            long failed = logins.stream().filter(l -> !Boolean.TRUE.equals(l.get("success"))).count();
            if (newDevice) {
                score += 0.25;
                reasons.add("successful login from an untrusted device (+0.25)");
            }
            if (geoMismatch) {
                score += 0.20;
                reasons.add("login geography does not match customer home country " + homeCountry + " (+0.20)");
            }
            if (failed >= 2) {
                score += 0.10;
                reasons.add(failed + " failed login attempts in recent history (+0.10)");
            }
        } else if (ev.called("device_login_history")) {
            reasons.add("no device/login history available (neutral)");
        }

        int priorCount = (int) number(prior.get("priorCaseCount"));
        if (priorCount > 0) {
            score += 0.10;
            reasons.add(priorCount + " prior dispute/fraud case(s) for this customer (+0.10)");
        }

        Map<String, Object> screening = ev.get("sanctions_screening");
        boolean screeningHit = Boolean.TRUE.equals(screening.get("hit"));
        if (screeningHit) {
            score += 0.40;
            reasons.add("sanctions/PEP screening hit on customer or merchant — "
                    + "mandatory compliance escalation (+0.40)");
        }

        Map<String, Object> velocity = ev.get("transaction_velocity");
        if (Boolean.TRUE.equals(velocity.get("velocityFlag"))) {
            score += 0.15;
            reasons.add("velocity flag: " + (long) number(velocity.get("countLast10Min"))
                    + " transactions within 10 minutes (+0.15)");
        }

        Map<String, Object> deviation = ev.get("amount_deviation");
        if (Boolean.TRUE.equals(deviation.get("deviationFlag"))) {
            score += 0.15;
            reasons.add(String.format("amount %.0f is %.1fx the customer's median baseline (+0.15)",
                    number(deviation.get("currentAmount")), number(deviation.get("deviationRatio"))));
        }

        Channel channel = channelOf(c, tx);
        if (channel == Channel.CARD_NOT_PRESENT || channel == Channel.ONLINE_BANKING || channel == Channel.WIRE) {
            score += 0.10;
            reasons.add(channel + " channel carries elevated fraud exposure (+0.10)");
        }
        if (amount >= rules.getHighAmount()) {
            score += 0.05;
            reasons.add(String.format("amount %.2f >= high-value threshold %.2f (+0.05)", amount, rules.getHighAmount()));
        }
        String kyc = String.valueOf(customer.getOrDefault("kycStatus", "VERIFIED"));
        if (!"VERIFIED".equalsIgnoreCase(kyc)) {
            score += 0.10;
            reasons.add("customer KYC status is " + kyc + " (+0.10)");
        }
        score = Math.min(1.0, score);
        reasons.add(0, String.format("composite fraud signal score %.2f", score));

        // ---- route decision ----
        if (screeningHit) {
            out.setRoute(CaseRoute.ESCALATE_FRAUD_OPS);
            out.setPriority(Priority.URGENT);
            reasons.add("screening hit mandates escalation to compliance/fraud ops — "
                    + "no autonomous action is taken");
        } else if (amount >= rules.getEscalateAmount()) {
            out.setRoute(CaseRoute.ESCALATE_FRAUD_OPS);
            out.setPriority(Priority.URGENT);
            reasons.add(String.format("amount %.2f >= escalation threshold %.2f", amount, rules.getEscalateAmount()));
        } else if (score >= rules.getFraudScoreEscalate()) {
            out.setRoute(CaseRoute.ESCALATE_FRAUD_OPS);
            out.setPriority(Priority.HIGH);
            reasons.add(String.format("fraud score %.2f >= escalation threshold %.2f", score, rules.getFraudScoreEscalate()));
        } else if (score >= rules.getFraudScoreFraudLikely()) {
            if (amount >= rules.getManualReviewAmount()) {
                out.setRoute(CaseRoute.MANUAL_REVIEW);
                reasons.add(String.format("fraud-likely score but amount %.2f >= manual review threshold %.2f",
                        amount, rules.getManualReviewAmount()));
            } else {
                out.setRoute(CaseRoute.RECOMMEND_REFUND);
                reasons.add(String.format("fraud score %.2f >= fraud-likely threshold %.2f", score, rules.getFraudScoreFraudLikely()));
            }
            out.setPriority(Priority.HIGH);
        } else if (score <= rules.getFraudScoreLegitLikely()) {
            out.setRoute(CaseRoute.RECOMMEND_DENIAL);
            out.setPriority(Priority.LOW);
            reasons.add(String.format("fraud score %.2f <= legitimacy threshold %.2f", score, rules.getFraudScoreLegitLikely()));
        } else {
            out.setRoute(CaseRoute.MANUAL_REVIEW);
            out.setPriority(Priority.MEDIUM);
            reasons.add(String.format("fraud score %.2f is between fraud-likely %.2f and legitimacy %.2f thresholds",
                    score, rules.getFraudScoreFraudLikely(), rules.getFraudScoreLegitLikely()));
        }

        out.setConfidence(Math.round((0.55 + 0.45 * Math.abs(score - 0.45) / 0.55) * 100.0) / 100.0);
        out.setSummary("Rules engine: " + out.getRoute() + " (fraud score " + String.format("%.2f", score) + ")");
        attachCitations(out, retrieval);
        return out;
    }

    private void attachCitations(RecommendationOutput out, RetrievalResult retrieval) {
        for (RetrievedPassage p : retrieval.getPassages()) {
            out.getPolicyCitations().add(PolicyCitation.of(
                    p.getChunk().getPolicyId(), p.getChunk().getPolicyVersion(),
                    p.getChunk().getSection(), p.getChunk().getPolicyTitle(),
                    p.getScore(), p.snippet(220)));
        }
    }

    private Channel channelOf(FraudCase c, Map<String, Object> tx) {
        if (c.getChannel() != null) return c.getChannel();
        try {
            return Channel.valueOf(String.valueOf(tx.getOrDefault("channel", "CARD_PRESENT")));
        } catch (Exception e) {
            return Channel.CARD_PRESENT;
        }
    }

    private static double number(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String safe(String s) {
        return s == null ? "(none provided)" : s;
    }
}
