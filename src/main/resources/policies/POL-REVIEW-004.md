# POL-REVIEW-004 — Human Review and Disposition Requirements (synthetic)

This is a fictional training policy for the capstone demonstration.

## 1. Mandatory Human Disposition

Every case must receive a final disposition from an authorized human
reviewer. The assistant's recommendation is advisory and is never the
disposition of record. Reviewers must see the recommendation, the
supporting reasons, the evidence collected and the policy citations.

## 2. Disposition Values

A reviewer may record: FRAUD_CONFIRMED when the dispute is accepted as
fraud; LEGITIMATE_TRANSACTION when the charge is determined valid;
ESCALATED_TO_SPECIALIST when specialist handling is required; or
MORE_EVIDENCE_REQUESTED to return the case for additional evidence.

## 3. Rationale Requirement

Every disposition must include a written rationale of at least ten
characters referencing the evidence considered. Dispositions without a
rationale are invalid and must not be recorded.

## 4. Audit Trail

All intake, investigation, recommendation and review activity must be
recorded in an append-only audit trail. Audit records may not be edited
or deleted; corrections are recorded as new events.
