# POL-FRAUD-002 — Unauthorized Transaction Indicators (synthetic)

This is a fictional training policy for the capstone demonstration.

## 1. Strong Indicators

The following signals are treated as strong indicators of an unauthorized
transaction: a successful authentication from a device that has never been
associated with the customer; an access geography inconsistent with the
customer's home country; a merchant with a high reported-fraud score; or
repeated failed login attempts immediately before the disputed activity.

## 2. Contributing Indicators

Contributing indicators include card-not-present or wire channels, a
disputed amount significantly above the customer's typical spend, prior
confirmed fraud on the account, and incomplete customer verification.

## 3. Exculpatory Indicators

Evidence consistent with legitimate use includes: transactions on a
trusted device from the customer's home geography, low merchant fraud
scores, merchants matching the customer's established spending pattern,
and card-present transactions with chip verification.

## 4. Scoring Guidance

A composite of strong indicators should weigh at least twice a single
contributing indicator. A case showing two or more strong indicators is
fraud-likely; a case showing only exculpatory indicators may be
recommended for denial of the fraud claim.
