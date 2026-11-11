# POL-DISPUTE-001 — Dispute Intake and Evidence Standards (synthetic)

This is a fictional training policy for the capstone demonstration.

## 1. Intake Requirements

Every fraud dispute case must record the customer identifier, a masked card
reference (last four digits only), the disputed amount and currency, the
transaction channel and the dispute type. Full card numbers, PINs or
payment credentials must never be collected or stored.

## 2. Minimum Evidence Set

Before a route can be recommended, the investigation must retrieve, at a
minimum: the disputed transaction record, the customer profile, and the
merchant risk profile. Device and login history and prior case history
should be retrieved whenever available.

## 3. Missing Evidence Handling

If any element of the minimum evidence set cannot be retrieved, the case
must not receive an approval or denial recommendation. The correct route
is REQUEST_EVIDENCE with an itemized list of the missing records.

## 4. Citation Requirements

Every recommended route must cite at least one policy passage supporting
the recommendation. Citations must include the policy identifier, version
and section. Recommendations that cannot be grounded in retrieved policy
must be abstained and routed to manual review.
