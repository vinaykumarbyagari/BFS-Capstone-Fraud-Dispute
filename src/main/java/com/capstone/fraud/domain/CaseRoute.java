package com.capstone.fraud.domain;

/**
 * Advisory route recommendations only. The agent never executes financial,
 * account-control or customer-contact actions; a human reviewer dispositions
 * every case.
 */
public enum CaseRoute {
    /** Evidence suggests the disputed transaction is likely fraud. */
    RECOMMEND_REFUND,
    /** Evidence suggests the transaction is likely legitimate. */
    RECOMMEND_DENIAL,
    /** Evidence is insufficient; request specific items. */
    REQUEST_EVIDENCE,
    /** High-risk indicators; route to fraud operations queue. */
    ESCALATE_FRAUD_OPS,
    /** Ambiguous or ungrounded; keep in manual review queue. */
    MANUAL_REVIEW
}
