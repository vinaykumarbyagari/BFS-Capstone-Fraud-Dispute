# POL-ROUTING-003 — Case Routing and Escalation Thresholds (synthetic)

This is a fictional training policy for the capstone demonstration.

## 1. Advisory Routes

The investigation assistant may recommend one of: RECOMMEND_REFUND when
fraud is likely and the amount is below the manual-review threshold;
RECOMMEND_DENIAL when evidence indicates the transaction is legitimate;
REQUEST_EVIDENCE when the minimum evidence set is incomplete;
ESCALATE_FRAUD_OPS for high-risk or high-value cases; or MANUAL_REVIEW
for ambiguous or ungrounded situations.

## 2. Escalation Thresholds

Any disputed amount of 10,000 USD or more must be routed to fraud
operations with URGENT priority. Fraud-likely cases of 5,000 USD or more
must be routed to manual review rather than receiving a refund
recommendation.

## 3. Priority Assignment

Priority is assigned as: URGENT for amounts at or above 10,000 USD or
active account-takeover signals; HIGH for fraud-likely composites;
MEDIUM for ambiguous cases and evidence requests; LOW for likely
legitimate transactions.

## 4. Safety Boundary

The assistant's output is advisory only. No route recommendation may
itself block a card, issue a refund, move funds, file a regulatory
report, or contact the customer. All such actions require human review
and separate systems of record.
