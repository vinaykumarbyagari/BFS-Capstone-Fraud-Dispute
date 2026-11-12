package com.capstone.fraud.policy;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.PolicyChunk;
import com.capstone.fraud.repo.PolicyChunkRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Retired policy chunks must never be retrieved or cited, even when
 * their text is the best lexical match for the query.
 */
class RetiredPolicyExclusionTest {

    private PolicyChunk chunk(String policyId, String status, String text) {
        PolicyChunk c = new PolicyChunk();
        c.setPolicyId(policyId);
        c.setPolicyVersion("1.0");
        c.setPolicyTitle("Policy " + policyId);
        c.setSection("Section 1");
        c.setStatus(status);
        c.setText(text);
        return c;
    }

    @Test
    void retiredChunkIsExcludedEvenWhenBestMatch() {
        PolicyChunkRepository repo = Mockito.mock(PolicyChunkRepository.class);
        Mockito.when(repo.findAll()).thenReturn(List.of(
                chunk("POL-LEGACY", "RETIRED",
                        "automatic refund every disputed card transaction without investigation escalation"),
                chunk("POL-ACTIVE", "ACTIVE", "disputes are triaged by channel and dispute type")));
        VectorPolicyRetriever r = new VectorPolicyRetriever(repo, new FraudProperties());
        r.index();

        RetrievalResult result = r.retrieve("automatic refund disputed card transaction", 3);
        assertTrue(result.getPassages().stream()
                .noneMatch(p -> "POL-LEGACY".equals(p.getChunk().getPolicyId())));
    }

    @Test
    void nullStatusIsTreatedAsActive() {
        PolicyChunkRepository repo = Mockito.mock(PolicyChunkRepository.class);
        PolicyChunk legacy = chunk("POL-OLD", null, "dispute triage channel type");
        Mockito.when(repo.findAll()).thenReturn(List.of(legacy));
        VectorPolicyRetriever r = new VectorPolicyRetriever(repo, new FraudProperties());
        r.index();
        assertFalse(r.retrieve("dispute triage channel", 1).getPassages().isEmpty());
    }
}
