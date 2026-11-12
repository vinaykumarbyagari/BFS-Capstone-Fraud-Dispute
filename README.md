# Intelligent Payment Fraud Investigation & Dispute Resolution Agent

An explainable agentic assistant for a bank dispute team. It collects fraud
evidence through a bounded tool catalog, retrieves applicable policy with
exact citations, recommends a case route — and always leaves the final
disposition to a human reviewer. Built with Java 21 / Spring Boot 3.5 with
**both** Spring AI and LangChain4j implementations behind a
framework-neutral contract, plus a deterministic rules mode.

## Safety boundary

The application may **recommend** a route, priority, or evidence request.
It must not — and does not — block a card, issue a refund, move funds,
file a report, or contact a customer. Every recommendation is advisory;
the reviewer disposition is the decision of record.

## Architecture

```
                     ┌──────────── Thymeleaf UI ────────────┐   ┌── /api/cases ─┐
                     │ intake · case detail · review · audit │   │  REST (JSON)  │
                     └───────────────┬───────────────────────┘   └──────┬────────┘
                                     │                                │
                                 CaseService ──► append-only AuditService
                                     │
                     ┌───────────────▼────────────────┐
                     │   FraudCaseProcessorResolver    │  fraud.processor.mode:
                     └───────────────┬────────────────┘  auto|rules|springai|langchain4j
              ┌──────────────────────┼────────────────────────┐
              ▼                      ▼                        ▼
   RulesFraudCaseProcessor  SpringAiFraudCaseProcessor  LangChain4jFraudCaseProcessor
              └───────── shared pipeline (AbstractFraudCaseProcessor) ──────────┘
                                     │
        plan ─► EvidenceToolExecutor ─► PolicyRetriever (EmbeddingStore)
              (8 read-only tools,        (abstains when context
               traced + budgeted)         is insufficient)
                                     │
                  RulesAnalyzer / model call ─► ContractValidator
                  (JSON-Schema validated;     (schema/recommendation.schema.json;
                   invalid output → retry      ungrounded citations dropped;
                   once → RULES_FALLBACK)      abstention → MANUAL_REVIEW)
                                     │
        Persisted: FraudCase · EvidenceRecord · InvestigationStep ·
        Recommendation · ReviewDecision · CaseMessage · PolicyChunk · AuditEvent
```

Key properties:

- **Bounded tools** — 8 read-only tools (`transaction_lookup`,
  `customer_profile`, `device_login_history`, `merchant_risk`,
  `prior_case_history`, `transaction_velocity`, `amount_deviation`,
  `sanctions_screening`) over deterministic synthetic fixtures. In AI modes
  the model chooses calls from the catalog; unknown/over-budget calls are
  recorded as `REJECTED`, never executed (`fraud.evidence.max-tool-calls`).
- **Intake deduplication** — the same `transactionRef` cannot be intaken
  twice; duplicates return HTTP 409 (API) or a form error (UI). Case
  numbers are seeded from the persisted maximum, so restarts cannot
  collide with existing rows.
- **Observable trace** — every plan, tool call, retrieval, model call,
  validation and decision is persisted as an `InvestigationStep` (visible
  in the case detail "Execution trace" tab).
- **Policy RAG** — policy markdown is chunked on `## ` headings with exact
  citation metadata (policyId, version, section, status, effectiveDate),
  embedded through a LangChain4j `EmbeddingModel` into an
  `InMemoryEmbeddingStore` (deterministic TF-IDF vectors, offline) and
  retrieved by cosine similarity. Only `ACTIVE` policies are indexed —
  retired documents are excluded. Below `fraud.retrieval.min-score`
  the run **abstains** and routes to `MANUAL_REVIEW`.
- **Structured output contract** — model output must validate against
  `schema/recommendation.schema.json` (one correction retry on failure,
  then rules fallback). Citations not present in the retrieved set are
  dropped; a grounded route requires ≥1 real citation.
- **Case memory** — `CaseMessage` rows are strictly scoped by `caseId` and
  surfaced through each framework's native memory: Spring AI
  `MessageWindowChatMemory` over a JPA `ChatMemoryRepository`, and
  LangChain4j `MessageWindowChatMemory` over a JPA `ChatMemoryStore`
  (`memory/CaseChatMemoryAdapter`). Window is bounded at 10 turns.
- **Prompt hardening** — case fields, tool evidence and policy passages
  are wrapped in `<case_data>` / `<tool_evidence>` / `<policy_passages>`
  delimiters and the system prompt explicitly treats their content as
  data, never instructions.
- **Observability** — Spring Boot Actuator exposes
  `/actuator/health` (+ `info`, `metrics`); JaCoCo coverage report is
  generated under `target/site/jacoco/` during `mvn test`.
- **Append-only audit** — `AuditEvent` blocks update/delete at the entity
  level; all lifecycle events are recorded with actor and details.

## Running

Requires Java 21 + Maven 3.8+. Defaults to a file-based H2 database
(`./data/fraudagent`) — zero external setup:

```bash
cp .env.example .env        # optional: set ANTHROPIC_API_KEY for AI modes
mvn spring-boot:run         # or: mvn package && java -jar target/fraud-dispute-agent-1.0.0.jar
```

Open **http://localhost:8090**.

| Role       | Username  | Password      | Override env var     |
|------------|-----------|---------------|----------------------|
| Analyst    | analyst   | analyst123    | `ANALYST_PASSWORD`   |
| Reviewer   | reviewer  | reviewer123   | `REVIEWER_PASSWORD`  |

### Processor modes (`FRAUD_PROCESSOR_MODE`)

| Mode          | Behavior                                             |
|---------------|------------------------------------------------------|
| `auto`        | Spring AI if `ANTHROPIC_API_KEY` set, else rules     |
| `rules`       | Deterministic rules engine (no key needed)           |
| `springai`    | Spring AI + Anthropic (rules fallback on failure)    |
| `langchain4j` | LangChain4j + Anthropic (rules fallback on failure)  |

## Workflow

1. Analyst creates a dispute at `/cases/new` (try `CUST-101` / `TXN-5001`
   for a fraud-likely scenario, `CUST-103` / `TXN-5003` for a legitimate
   one, `TXN-9999` for the missing-evidence path).
2. **Run investigation** → evidence tools execute (traced), policy is
   retrieved, a schema-validated recommendation is produced.
3. Reviewer opens `/cases/{id}/review`, sees route + reasons + citations,
   and records the **final disposition with a rationale**.
4. Everything lands in the append-only audit trail (`/audit`).

### REST API

- `POST /api/cases` — intake (`"investigateNow": true` runs the pipeline)
- `POST /api/cases/{id}/investigate` — run investigation
- `GET  /api/cases/{id}` — case + latest recommendation
- `GET  /api/cases/{id}/trace` — evidence + execution steps
- `GET  /api/cases/{id}/audit` — case audit events
- `POST /api/cases/{id}/review` — `{disposition, rationale}`

```bash
curl -u analyst:analyst123 -X POST localhost:8090/api/cases \
  -H 'Content-Type: application/json' -d '{
    "customerId":"CUST-101","cardLast4":"4412","transactionRef":"TXN-5001",
    "merchantName":"SKYLINE ELECTRONICS","amount":1240.00,"currency":"USD",
    "channel":"CARD_NOT_PRESENT","disputeType":"UNAUTHORIZED_TRANSACTION",
    "description":"customer denies this purchase","investigateNow":true}'
```

## Tests

```bash
mvn test
```

- **Unit** — rules analyzer (incl. velocity/deviation/screening signals),
  JSON-schema contract, JSON extractor, embedding retriever (incl.
  abstention and retired-policy exclusion), new evidence tools,
  append-only audit enforcement.
- **Contract** — all three `FraudCaseProcessor` modes registered; AI modes
  unavailable without credentials; resolver falls back to rules.
- **Resilience** — model failure degrades to `RULES_FALLBACK`; unknown and
  over-budget tool calls are `REJECTED`; duplicate intake is rejected;
  memory is isolated per case and bounded.
- **Integration** — full intake → investigate → review flow via MockMvc
  with audit-trail assertions.
- **Evaluation** — golden scenarios asserting expected routes
  (`ESCALATE_FRAUD_OPS`, `RECOMMEND_DENIAL`, `REQUEST_EVIDENCE`, `URGENT`).

## Screenshots & self-review

Captured screens live in [`docs/screenshots/`](docs/screenshots/)
(dashboard, intake, case detail with evidence + trace, review, audit,
policies, login). A deliverable-by-deliverable assessment with known
limitations is in [`SELF_REVIEW.md`](SELF_REVIEW.md).

## Layout

```
src/main/java/com/capstone/fraud/
  ai/          processor contract + shared pipeline + rules/SpringAI/LC4J impls
  policy/      ingestion + vector retriever + citations
  tools/       evidence tool catalog + traced, budgeted executor
  domain/      entities + enums
  service/     case service, audit, tracer, case memory
  web/         Thymeleaf controllers + REST API
src/main/resources/
  fixtures/    deterministic synthetic data (training only)
  policies/    synthetic policy docs + manifest (policies.yml)
  schema/      recommendation.schema.json (output contract)
  templates/   UI screens
```

## Out of scope

Production customer data, payment credentials, unmasked identifiers, real
bank connectivity; autonomous financial/account-control/compliance/
customer-contact actions; unbounded tool permissions; hidden
chain-of-thought (concise reasons, evidence and citations are shown
instead).
