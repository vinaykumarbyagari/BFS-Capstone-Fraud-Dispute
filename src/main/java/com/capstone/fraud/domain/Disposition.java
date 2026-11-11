package com.capstone.fraud.domain;

/** Final disposition recorded by the human reviewer. */
public enum Disposition {
    FRAUD_CONFIRMED,
    LEGITIMATE_TRANSACTION,
    ESCALATED_TO_SPECIALIST,
    MORE_EVIDENCE_REQUESTED
}
