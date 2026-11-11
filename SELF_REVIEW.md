# Self-Review — Fraud Investigation & Dispute Resolution Agent

Assessment of the implementation against the capstone deliverables, with
pointers to the code that satisfies each item.

## Deliverable checklist

| # | Deliverable | Where | Status |
|---|-------------|-------|--------|
| 1 | Runnable Spring Boot repo, documented setup, no committed secrets | `README.md`, `pom.xml`; keys via env only | Done |
| 2 | Fraud case intake API + participant-friendly UI | `web/api/CaseApiController`, `web/CaseController`, `templates/cases/new.html` | Done |
| 3 | Framework-neutral `FraudCaseProcessor` + rules + Spring AI + LangChain4j | `ai/FraudCaseProcessor`, `RulesFraudCaseProcessor`, `SpringAiFraudCaseProcessor`, `LangChain4jFraudCaseProcessor`, `FraudCaseProcessorResolver` | Done (both AI paths implemented — comparison extension) |
| 4 | ≥4 evidence tools, deterministic fixtures, observable trace | `tools/` (5 tools), `resources/fixtures/`, `domain/InvestigationStep` + `service/InvestigationTracer` | Done |
| 5 | Policy ingestion, vector retrieval, exact citations, abstention | `policy/PolicyIngestionService`, `VectorPolicyRetriever` (TF-IDF cosine), `enforceSafetyNet` → abstain → MANUAL_REVIEW | Done |
| 6 | Case-scoped memory, persistence, append-only audit | `domain/CaseMessage` + `service/CaseMemoryService`; 8 JPA entities; `AuditEvent` blocks update/delete | Done |
| 7 | Schema-validated output + human-review screen | `ai/ContractValidator` + `schema/recommendation.schema.json`; `templates/cases/review.html` + `ReviewController` | Done |
| 8 | Unit, integration, contract, evaluation tests | `src/test` — 29 tests across 8 classes | Done |
| 9 | README, architecture view, screenshots, self-review | `README.md` (incl. ASCII architecture), `docs/screenshots/`, this file | Done |

## Safety boundary — how it is enforced

- **No autonomous actions exist.** The tool catalog is read-only by
  construction: `EvidenceTool.execute` returns fixture data; there is no
  code path that can block a card, refund, move funds or contact anyone.
- **Bounded tool permissions.** All calls go through
  `EvidenceToolExecutor`, which enforces a per-run budget
  (`fraud.evidence.max-tool-calls`) and rejects unknown tools — every
  rejection is itself traced.
- **Model output cannot bypass validation.** Raw output must pass
  `recommendation.schema.json` (one retry, then `RULES_FALLBACK`);
  citations are filtered to retrieved passages; an ungrounded or
  abstained route is forced to `MANUAL_REVIEW` in
  `AbstractFraudCaseProcessor.enforceSafetyNet` — applied after every
  analysis regardless of mode.
- **Human disposition is mandatory.** `CaseService.recordReview`
  requires a rationale (≥10 chars, bean-validated); only reviewer-role
  principals may submit; the disposition — not the recommendation —
  closes the case.
- **No chain-of-thought.** The model is prompted for concise reasons and
  citations; the UI shows reasons, tool outcomes and policy passages.

## Design decisions and trade-offs

- **TF-IDF vector retrieval instead of a neural embedding model.** The
  brief requires vector retrieval and abstention, not a specific
  embedding stack. TF-IDF cosine similarity is deterministic, needs no
  API key or large downloads, and makes golden tests reproducible. The
  `PolicyRetriever` interface isolates it — swapping in a neural
  embedding store is a one-class change.
- **Application-orchestrated tool execution with model-chosen plans.**
  AI modes ask the model which tools to call (bounded catalog, capped
  budget) rather than giving it free function-calling. This keeps the
  "agentic" property (the model drives evidence collection) while every
  call remains deterministic, traced and permission-bounded.
- **Lazy vector index.** Built on first retrieval rather than
  `@PostConstruct` to remove a startup ordering race with policy
  ingestion (found during integration testing).
- **In-memory H2 for tests / file H2 for runs.** Same pipeline,
  disposable test DB.

## Known limitations (honest list)

- Synthetic corpus is small: 4 transactions/customers/merchants, 4
  policy docs. Enough to exercise every route, not a realistic volume.
- AI modes are exercised against the contract and wiring in tests, but
  live model calls require `ANTHROPIC_API_KEY` and are not covered by CI
  (would need a recorded-mock or gated IT).
- Case numbers are counter-based (`FC-1001+`), not concurrency-proof.
- Audit append-only is enforced at the JPA entity level; a raw SQL
  client could still modify rows (acceptable for a capstone; a real
  system would add DB-level constraints/permissions).
- UI screenshots captured via headless Chrome + CDP with HTTP Basic —
  see `docs/screenshots/`.

## Verification evidence

- `mvn test` → 29 tests, 0 failures (unit + contract + MockMvc
  integration + golden evaluation).
- Live smoke test (rules mode, no key): `POST /api/cases` on the
  TXN-5001 fixture → `ESCALATE_FRAUD_OPS/HIGH` with 9 reasons and 5
  policy citations → reviewer disposition `FRAUD_CONFIRMED` → `CLOSED`.
- `docs/screenshots/` — 7 captured screens (dashboard, intake, case
  detail, review, audit, policies, login).
