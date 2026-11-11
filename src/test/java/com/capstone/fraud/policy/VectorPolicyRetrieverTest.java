package com.capstone.fraud.policy;

import com.capstone.fraud.config.FraudProperties;
import com.capstone.fraud.domain.PolicyChunk;
import com.capstone.fraud.repo.PolicyChunkRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorPolicyRetrieverTest {

    private VectorPolicyRetriever retriever(List<PolicyChunk> chunks) {
        PolicyChunkRepository repo = Mockito.mock(PolicyChunkRepository.class);
        Mockito.when(repo.findAll()).thenReturn(chunks);
        VectorPolicyRetriever r = new VectorPolicyRetriever(repo, new FraudProperties());
        r.index();
        return r;
    }

    private PolicyChunk chunk(String policyId, String section, String text) {
        PolicyChunk c = new PolicyChunk();
        c.setPolicyId(policyId);
        c.setPolicyVersion("1.0");
        c.setPolicyTitle("Test policy " + policyId);
        c.setSection(section);
        c.setText(text);
        return c;
    }

    @Test
    void ranksRelevantPassageFirst() {
        VectorPolicyRetriever r = retriever(List.of(
                chunk("POL-A", "Escalation", "amounts above ten thousand must escalate urgently"),
                chunk("POL-B", "Devices", "new device login geography unauthorized indicator")));
        RetrievalResult result = r.retrieve("unauthorized login from new device geography", 2);
        assertTrue(result.isSufficient());
        assertEquals("POL-B", result.getPassages().get(0).getChunk().getPolicyId());
    }

    @Test
    void abstainsWhenNothingMatches() {
        VectorPolicyRetriever r = retriever(List.of(
                chunk("POL-A", "Escalation", "amounts above ten thousand must escalate urgently")));
        RetrievalResult result = r.retrieve("zebra quilt quantum", 2);
        assertFalse(result.isSufficient());
    }

    @Test
    void abstainsWhenIndexEmpty() {
        VectorPolicyRetriever r = retriever(List.of());
        assertFalse(r.retrieve("anything", 2).isSufficient());
    }
}
