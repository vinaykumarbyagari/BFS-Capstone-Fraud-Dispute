package com.capstone.fraud.ai;

import com.capstone.fraud.domain.CaseMessage;
import com.capstone.fraud.domain.EvidenceRecord;
import com.capstone.fraud.domain.FraudCase;
import com.capstone.fraud.policy.RetrievedPassage;
import com.capstone.fraud.tools.EvidenceToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builds the prompts for the two agent steps: tool planning and
 * recommendation analysis. Prompts carry only synthetic case data,
 * bounded tool catalog and retrieved policy passages — they instruct
 * the model to emit concise reasons and citations, never chain-of-thought.
 */
@Component
public class PromptFactory {

    private final EvidenceToolExecutor executor;
    private final ObjectMapper objectMapper;

    public PromptFactory(EvidenceToolExecutor executor, ObjectMapper objectMapper) {
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    public String planSystem() {
        return """
                You are the planning component of a payment-fraud investigation assistant.
                Select which evidence tools to call to investigate the disputed transaction.
                You may ONLY use the tools listed below. Prefer calling every tool that could
                inform the decision; a typical investigation uses most of them.

                Available tools:
                %s

                Respond with ONLY a JSON object (no markdown, no commentary):
                {"toolCalls":[{"tool":"<tool name>","arguments":{"<arg>":"<value>"}}]}
                """.formatted(executor.catalogDescription());
    }

    public String planUser(FraudCase c) {
        return "Case to investigate:\n" + caseJson(c);
    }

    public String analyzeSystem() {
        return """
                You are a payment-fraud investigation assistant for a bank dispute team.

                STRICT BOUNDARIES — you are advisory only:
                - You may recommend a route, a priority, and evidence requests.
                - You must NEVER claim to block a card, issue a refund, move funds,
                  file a report, or contact the customer. A human reviewer makes
                  the final decision.

                Output requirements:
                - Respond with ONLY a JSON object matching this shape (no markdown):
                  {"route":"RECOMMEND_REFUND|RECOMMEND_DENIAL|REQUEST_EVIDENCE|ESCALATE_FRAUD_OPS|MANUAL_REVIEW",
                   "priority":"LOW|MEDIUM|HIGH|URGENT",
                   "confidence":<number 0-1>,
                   "summary":"<one or two sentences for the reviewer>",
                   "reasons":["<concise evidence-based reason>", ...],
                   "policyCitations":[{"policyId":"<id from a provided passage>","policyVersion":"<v>","section":"<s>","title":"<t>","score":<n>,"snippet":"<short quote>"}],
                   "evidenceRequests":[{"item":"<what>","reason":"<why>"}],
                   "abstained":<bool>,
                   "abstainReason":"<why or null>"}
                - Cite ONLY policy passages provided below, copying their exact
                  policyId/version/section. If no provided passage supports a
                  confident route, set abstained=true and route=MANUAL_REVIEW.
                - If key evidence is missing, prefer REQUEST_EVIDENCE and list
                  what is needed in evidenceRequests.
                - Show concise reasons grounded in the evidence — do NOT reveal
                  chain-of-thought.
                """;
    }

    public String analyzeUser(FraudCase c, List<EvidenceRecord> evidence,
                              List<RetrievedPassage> passages, List<CaseMessage> memory) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Case\n").append(caseJson(c)).append("\n\n## Evidence collected by tools\n");
        for (EvidenceRecord r : evidence) {
            sb.append("- ").append(r.getToolName())
                    .append(" args=").append(r.getArgumentsJson())
                    .append(" outcome=").append(r.getOutcome())
                    .append(" output=").append(r.getOutputJson())
                    .append("\n");
        }
        sb.append("\n## Retrieved policy passages (cite only these)\n");
        int i = 1;
        for (RetrievedPassage p : passages) {
            sb.append("[").append(i++).append("] ").append(p.citationKey())
                    .append(" \"").append(p.getChunk().getPolicyTitle()).append("\"\n")
                    .append(p.snippet(500)).append("\n\n");
        }
        if (passages.isEmpty()) {
            sb.append("(no policy passages retrieved)\n");
        }
        if (memory != null && !memory.isEmpty()) {
            sb.append("\n## Prior case memory (this case only)\n");
            for (CaseMessage m : memory) {
                sb.append("- ").append(m.getRole()).append(": ")
                        .append(m.getContent().length() > 600
                                ? m.getContent().substring(0, 600) + "…" : m.getContent())
                        .append("\n");
            }
        }
        sb.append("\nProduce the recommendation JSON now.");
        return sb.toString();
    }

    private String caseJson(FraudCase c) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "caseNumber", nullToEmpty(c.getCaseNumber()),
                    "customerId", nullToEmpty(c.getCustomerId()),
                    "cardLast4", nullToEmpty(c.getCardLast4()),
                    "transactionRef", nullToEmpty(c.getTransactionRef()),
                    "merchantName", nullToEmpty(c.getMerchantName()),
                    "amount", c.getAmount() == null ? "" : c.getAmount().toPlainString(),
                    "currency", nullToEmpty(c.getCurrency()),
                    "channel", c.getChannel() == null ? "" : c.getChannel().name(),
                    "disputeType", c.getDisputeType() == null ? "" : c.getDisputeType().name(),
                    "description", nullToEmpty(c.getDescription())));
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
