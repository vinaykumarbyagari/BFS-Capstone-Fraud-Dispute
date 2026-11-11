package com.capstone.fraud.ai;

import com.capstone.fraud.domain.FraudCase;
import com.capstone.fraud.domain.ProcessorMode;
import com.capstone.fraud.dto.ProcessingOutcome;

/**
 * Framework-neutral investigation contract. Implemented by the rules
 * engine, the Spring AI path and the LangChain4j path. The processor
 * may recommend a route, priority or evidence request — it never
 * executes financial, account-control or customer-contact actions.
 */
public interface FraudCaseProcessor {

    ProcessorMode mode();

    /** False when a required backend (e.g. model credentials) is missing. */
    boolean available();

    ProcessingOutcome investigate(FraudCase fraudCase);
}
