package com.capstone.fraud.service;

import com.capstone.fraud.domain.Channel;
import com.capstone.fraud.domain.DisputeType;
import com.capstone.fraud.domain.FraudCase;
import com.capstone.fraud.dto.CaseIntakeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Intake deduplication: submitting the same transaction reference twice
 * must surface a clear conflict, not a duplicate case or HTTP 500.
 */
@SpringBootTest
class IntakeDeduplicationTest {

    @Autowired
    CaseService caseService;

    private CaseIntakeRequest req(String txnRef) {
        CaseIntakeRequest r = new CaseIntakeRequest();
        r.setCustomerId("CUST-101");
        r.setCardLast4("4412");
        r.setTransactionRef(txnRef);
        r.setMerchantName("SKYLINE ELECTRONICS");
        r.setAmount(new BigDecimal("1240.00"));
        r.setCurrency("USD");
        r.setChannel(Channel.CARD_NOT_PRESENT);
        r.setDisputeType(DisputeType.UNAUTHORIZED_TRANSACTION);
        r.setDescription("dedup test");
        return r;
    }

    @Test
    void duplicateTransactionRefIsRejected() {
        FraudCase first = caseService.intake(req("TXN-DEDUP-1"), "test");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> caseService.intake(req("TXN-DEDUP-1"), "test"));
        assertTrue(ex.getMessage().contains(first.getCaseNumber()));
    }

    @Test
    void distinctTransactionRefsGetDistinctCaseNumbers() {
        FraudCase a = caseService.intake(req("TXN-DEDUP-A"), "test");
        FraudCase b = caseService.intake(req("TXN-DEDUP-B"), "test");
        assertNotEquals(a.getCaseNumber(), b.getCaseNumber());
        assertTrue(a.getCaseNumber().matches("FC-\\d+"));
        assertTrue(b.getCaseNumber().matches("FC-\\d+"));
    }
}
