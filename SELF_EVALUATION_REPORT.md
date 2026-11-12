# Capstone Self-Evaluation Report

## 1. Executive Summary
- Project: `fraud-dispute-agent` — Intelligent Payment Fraud Investigation and Dispute Resolution Agent
- Primary framework: Spring AI (`spring-ai-anthropic` 1.0.9, `AnthropicChatModel`)
- Secondary framework: LangChain4j (`langchain4j-anthropic` 1.5.4, `AnthropicChatModel`)
- Build status: `mvn clean test` BUILD SUCCESS; `mvn verify` BUILD SUCCESS (jar packaged)
- Test status: **46 tests run — 0 failures, 0 errors, 0 skipped**
- Overall score: **90/100**
- Percentage: 90%
- Readiness: Demo Ready
- Scoring model used: Both frameworks implemented → metrics 4 and 5 scored at 10 points each, parity evaluated (§5.3). Total = 100.
- Change since previous evaluation (76→90): the Critical restart-collision bug is fixed and verified live; intake deduplication added; policy lifecycle metadata with retired-policy exclusion implemented and tested; three new evidence tools added; a real LangChain4j `EmbeddingStore`/`EmbeddingModel` now backs retrieval; both frameworks use native chat memory over a JPA store; prompts gained untrusted-data delimiters; Actuator + JaCoCo added; automated tests grew 29→46.

## 2. Repository and Technology Overview
| Area | Finding | Evidence |
|---|---|---|
| Business objective | Advisory agent for a bank dispute team: collect fraud evidence via bounded tools, cite policy, recommend a route; human reviewer dispositions | `README.md`, `ai/FraudCaseProcessor` javadoc |
| Java / Spring Boot | Java 21, Spring Boot 3.5.6 | `pom.xml` |
| Build tool / deps | Maven; web, thymeleaf, data-jpa, security, validation, **actuator**, h2, lombok, spring-ai-anthropic, langchain4j(-anthropic), everit-json-schema 1.14.4, JaCoCo 0.8.12 | `pom.xml` |
| AI frameworks | **Both** Spring AI and LangChain4j, behind a shared pipeline | `ai/SpringAiFraudCaseProcessor`, `ai/LangChain4jFraudCaseProcessor`, `ai/AbstractAiFraudCaseProcessor` |
| Model provider | Anthropic Claude via HTTP messages API; key/model/base-url externalized (`ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL`, `ANTHROPIC_BASE_URL`); evaluated with `global.anthropic.claude-sonnet-4-6` via a gateway | `config/FraudProperties`, `application.yml`, processor `init()` methods |
| Database / persistence | H2 file DB (`jdbc:h2:file:./data/fraudagent`) via Spring Data JPA, `ddl-auto: update`; in-memory H2 for tests | `application.yml`, `src/test/resources/application.yml`, domain entities + `repo/` |
| Embedding store | **LangChain4j `InMemoryEmbeddingStore<TextSegment>`** fed by `PolicyEmbeddingModel` (a deterministic local `EmbeddingModel` producing dense TF-IDF vectors — reproducible offline); cosine search with `EmbeddingSearchRequest` top-K + min-score | `policy/VectorPolicyRetriever`, `policy/PolicyEmbeddingModel` |
| Chat memory | **Framework-native**: Spring AI `MessageWindowChatMemory` over a JPA `ChatMemoryRepository`; LangChain4j `MessageWindowChatMemory` over a JPA `ChatMemoryStore`; conversation/memory id = case id, window = 10 | `memory/CaseChatMemoryAdapter`, both processors' `callModel` |
| UI | Thymeleaf server-rendered screens (dashboard, intake, detail+trace, review, audit, policies, login, error); all form controls have associated labels | `templates/`, `web/*Controller` |
| MCP / external integrations | None | no MCP config or client code found |
| Entry point | `com.capstone.fraud.FraudDisputeApplication` | `FraudDisputeApplication.java` |
| Framework-neutral interfaces | `FraudCaseProcessor` (investigate/mode/available), `EvidenceTool`, `PolicyRetriever` | `ai/`, `tools/`, `policy/` packages |
| Config modes | `fraud.processor.mode` = `auto`/`rules`/`springai`/`langchain4j` via `FRAUD_PROCESSOR_MODE` | `application.yml`, `FraudCaseProcessorResolver` |
| Observability | Actuator `/actuator/health|info|metrics`; persisted `InvestigationStep` trace; append-only `AuditEvent`; JaCoCo `target/site/jacoco` | `application.yml`, `service/InvestigationTracer`, `pom.xml` |

## 3. Build and Test Results
| Command | Result | Details |
|---|---|---|
| `mvn clean test` | BUILD SUCCESS | **Tests run: 46, Failures: 0, Errors: 0, Skipped: 0** |
| `mvn verify` | BUILD SUCCESS | Same 46 tests + `fraud-dispute-agent-1.0.0.jar` packaged |
| Coverage | JaCoCo report generated | `target/site/jacoco/index.html` (plugin configured in `pom.xml`) |
| Warnings | Non-fatal | javac annotation-processing notice; duplicate `org.json` classes in test scope; Mockito dynamic-agent self-attach warning; spring-jcl commons-logging notice |
| Credential-gated tests | None failing | Tests run in `rules` mode with empty API key; AI processors verified "unavailable without credentials" by `ProcessorContractTest` — by design |

Test inventory (all passing): `RulesAnalyzerTest` (4), `ContractValidatorTest` (6), `JsonExtractorTest` (4), `VectorPolicyRetrieverTest` (3), `RetiredPolicyExclusionTest` (2), `ProcessorContractTest` (3), `AuditAppendOnlyTest` (2), `NewEvidenceToolsTest` (6), `ExecutorBoundariesTest` (3), `IntakeDeduplicationTest` (2), `CaseMemoryIsolationTest` (3), `AiFallbackTest` (1), `CaseApiIntegrationTest` (3), `GoldenEvaluationTest` (4).

## 4. Evaluation Scorecard
| # | Metric | Maximum | Score | Status | Evaluator Comments | Evidence |
|---|---|---:|---:|---|---|---|
| 1 | BFS fraud and dispute use case | 10 | 10 | Implemented | Full intake→investigate→review lifecycle with synthetic IDs, amounts, channels, merchants, dispute types. Route enums are semantically equivalent to rubric names (`RECOMMEND_REFUND/DENIAL`, `REQUEST_EVIDENCE`, `ESCALATE_FRAUD_OPS`, `MANUAL_REVIEW`). **Sanctions/PEP screening now provides an AML escalation lane** (`mandatoryEscalation` → compliance review, never autonomous action). No loan-underwriting concepts. | `CaseIntakeRequest`, `CaseStatus`, `CaseRoute`, `SanctionsScreeningTool`, fixtures |
| 2 | Architecture and maintainability | 12 | 12 | Implemented | Clean package layering; framework-neutral interfaces; thin AI adapters over a shared pipeline (zero duplicated business rules); config-driven resolver; typed domain models; **case-number generation now seeded from persisted rows (restart-safe) and `DataIntegrityViolationException` maps to a clean 409**. | `ai/AbstractFraudCaseProcessor`, `CaseService.seedCaseCounter`, `CaseApiController.dataConflict` |
| 3 | Investigation workflow and deterministic tools | 15 | 14 | Implemented | **8 read-only tools** over deterministic fixtures: `transaction_lookup`, `customer_profile`, `device_login_history`, `merchant_risk`, `prior_case_history`, **`transaction_velocity`** (5+/10min flag), **`amount_deviation`** (>5x median-baseline flag), **`sanctions_screening`** (synthetic watchlist/PEP, mandatory-escalation signal). Model-chosen JSON plans execute only via `EvidenceToolExecutor` (budget 8, unknown → `REJECTED`, failures → `ERROR`); every call persisted with args/outcome/duration. Only a standalone policy-lookup tool is absent — policy context comes via `PolicyRetriever` instead. | `tools/` package, `EvidenceToolExecutor`, `ExecutorBoundariesTest`, `NewEvidenceToolsTest` |
| 4 | Spring AI implementation | 10 | 8 | Implemented | `AnthropicChatModel.call(Prompt)` ✓; system+user prompts ✓; externalized config incl. gateway `baseUrl` ✓; schema-validated output + retry → `RULES_FALLBACK` ✓; **framework `MessageWindowChatMemory` over JPA `ChatMemoryRepository`, conversation id = case id** ✓. Not used: `PromptTemplate`, `@Tool` callbacks (orchestrated plans instead), `entity()` converters, advisors, Spring AI `VectorStore` (LC4J store used). | `SpringAiFraudCaseProcessor.callModel`, `CaseChatMemoryAdapter` |
| 5 | LangChain4j implementation | 10 | 8 | Implemented | `AnthropicChatModel.chat(ChatRequest)` ✓; externalized config ✓; same validation/fallback ✓; **`MessageWindowChatMemory` + `ChatMemoryStore` with memory id = case id** ✓; **`EmbeddingModel` + `InMemoryEmbeddingStore` + `EmbeddingSearchRequest` for policy retrieval** ✓. Not used: AI Services interfaces, `@SystemMessage`/`@UserMessage`/`@Tool` annotations, `RetrievalAugmentor`. | `LangChain4jFraudCaseProcessor.callModel`, `VectorPolicyRetriever`, `CaseChatMemoryAdapter` |
| 6 | RAG, embeddings, retrieval, citations | 15 | 12 | Implemented | Manifest-driven ingestion (idempotent per document); `## `-heading chunking with policyId/version/section/title/**status**/**effectiveDate** metadata; **retired policies excluded at index time (tested incl. a RETIRED fixture doc `POL-LEGACY-099`)**; dense-vector embedding via LC4J `EmbeddingModel` into `InMemoryEmbeddingStore`; configurable top-K (5) + min-score (0.10); zero-vocabulary queries abstain; ungrounded citations dropped; grounded route requires ≥1 real citation. Honest remainder: embeddings are deterministic lexical vectors, not a neural model — paraphrase robustness is bounded; no conflicting-policy test. | `VectorPolicyRetriever`, `PolicyEmbeddingModel`, `PolicyIngestionService`, `RetiredPolicyExclusionTest` |
| 7 | Structured output, memory, human review | 8 | 8 | Implemented | Typed `RecommendationOutput` validated against draft-07 JSON schema pre-persistence; one correction retry → `RULES_FALLBACK`. **Framework chat memory (both adapters) JPA-backed, bounded to 10 turns, case-id isolated — isolation + window tested.** Reviewer dispositions with mandatory ≥10-char rationale; REVIEWER role required; any disposition overrides with mandatory rationale. | `ContractValidator`, `recommendation.schema.json`, `CaseMemoryIsolationTest`, `ReviewForm` |
| 8 | Security, privacy, responsible AI, auditability | 8 | 7 | Implemented | No secrets in git (`.env` ignored, grep-verified); synthetic data only; card = last-4-only regex-enforced. Read-only tool catalog = least privilege. runId correlation + actor-attributed append-only audit + `modelName` recorded. **Prompt injection hardened: case/evidence/policy text wrapped in `<case_data>`/`<tool_evidence>`/`<policy_passages>` delimiters and the system prompt declares them data-never-instructions; live injection test held.** Remaining lows: hardcoded demo password defaults, CSRF-exempt `/api/**`, JPA-level (not DB-level) audit immutability. | `PromptFactory`, `SecurityConfig`, `AuditEvent`, FC-1006 injection run |
| 9 | Testing, reliability, observability | 8 | 7 | Implemented | **46 green tests** incl. tool unit tests, executor boundary tests (unknown/budget → REJECTED), dedup, retired-policy exclusion, memory isolation + window bound, **AI failure → `RULES_FALLBACK` injection test**, golden evaluations, MockMvc end-to-end. Persisted per-step trace with durations; **Actuator health/info/metrics live-verified; JaCoCo configured**. Remaining: no automated live-model parity test (parity verified manually live), no CI pipeline, no token-usage capture. | `src/test/` (14 classes), `InvestigationTracer`, `/actuator/health` |
| 10 | UI, documentation, developer experience | 4 | 4 | Implemented | Complete Thymeleaf UI incl. evidence + execution-trace tabs, review, audit, policies pages; **all form controls have associated `for` labels**; duplicate-intake errors render on the form field; README covers architecture, modes, API, creds, limitations. Screenshots committed. | `templates/` (label sweep), `README.md`, `docs/screenshots/` |
|  | **Total** | **100** | **90** |  |  |  |

## 5. Framework Evaluation
### 5.1 Spring AI
- Usage: manual `AnthropicApi`/`AnthropicChatModel` bean (works behind a gateway via `baseUrl`), temp 0.1, maxTokens 2048.
- Memory: `MessageWindowChatMemory` (maxMessages=10) over `CaseChatMemoryAdapter` (JPA `ChatMemoryRepository`); conversation id = case id; windowed history is placed between the system and user messages.
- Agent behavior: model emits a JSON `toolCalls` plan → bounded executor → schema-bound recommendation; one validation retry; failure → `RULES_FALLBACK` with traced `FALLBACK` step (`AiFallbackTest` covers this automatically).
- Verified live: `ESCALATE_FRAUD_OPS`/URGENT, schemaValid=true, `spring-ai:global.anthropic.claude-sonnet-4-6`; FC-1008 (`CUST-102`/`TXN-5002`) `ESCALATE_FRAUD_OPS`/HIGH with all 8 tools executed.
### 5.2 LangChain4j
- Usage: `dev.langchain4j.model.anthropic.AnthropicChatModel` with apiKey/modelName/baseUrl (`{root}/v1/` normalization), `model.chat(ChatRequest)` with system + windowed-history + user messages.
- Memory: LC4J `MessageWindowChatMemory` (maxMessages=10, id=caseId) over `CaseChatMemoryAdapter` (JPA `ChatMemoryStore`).
- Retrieval: `PolicyEmbeddingModel` implements `dev.langchain4j.model.embedding.EmbeddingModel`; chunks stored as `TextSegment`s in `InMemoryEmbeddingStore` and searched via `EmbeddingSearchRequest` — genuine framework embedding-store integration.
- Verified live: `ESCALATE_FRAUD_OPS`/URGENT, schemaValid=true, `langchain4j:global.anthropic.claude-sonnet-4-6`.
### 5.3 Framework Parity
Same intake (CUST-101/TXN-5001, $1240 CARD_NOT_PRESENT) run through both adapters live:

| Aspect | Spring AI | LangChain4j | Match |
|---|---|---|---|
| Route | ESCALATE_FRAUD_OPS | ESCALATE_FRAUD_OPS | ✓ |
| Priority / confidence | URGENT / 0.93 | URGENT / 0.93 | ✓ |
| Tool calls | full catalog, model-planned | full catalog, model-planned | ✓ |
| Schema validity | true | true | ✓ |
| Citations | POL-FRAUD-002, POL-DISPUTE-001 | POL-FRAUD-002, POL-DISPUTE-001 | ✓ |
| Memory | `MessageWindowChatMemory` + JPA repo, id=caseId | `MessageWindowChatMemory` + JPA store, id=caseId | ✓ |
| Abstain / failure behavior | identical shared pipeline | identical shared pipeline | ✓ |

Business outcome, evidence set, citations, memory isolation and safety behavior are equivalent — only phrasing differs. Parity remains verified live rather than by an automated mock-model parity test (noted in §8).

## 6. Acceptance Scenario Results
| Scenario | Mode | Expected | Actual | Result | Evidence |
|---|---|---|---|---|---|
| Known device, familiar merchant, normal amount (CUST-103/TXN-5003) | SPRING_AI | Genuine/low-risk, human approval | `RECOMMEND_DENIAL`/LOW, exculpatory reasoning, AWAITING_REVIEW | PASS | prior live run FC-1004; `GoldenEvaluationTest.legitimateScenarioRecommendsDenial` |
| New device, unusual location, high amount (CUST-101/TXN-5001, RO, untrusted device, merchant 0.82) | SPRING_AI | Suspicious/unauthorised + cited evidence | `ESCALATE_FRAUD_OPS`/URGENT, grounded reasons + citations | PASS | FC-1003; `GoldenEvaluationTest.fraudLikelyScenarioEscalates` |
| Same scenario via second framework | LANGCHAIN4J | Equivalent outcome | `ESCALATE_FRAUD_OPS`/URGENT, same citations | PASS | FC-1007 |
| ≥5 transactions in 10 min (velocity) | RULES/tool test | Velocity indicator recorded | `transaction_velocity` reports `countLast10Min`≥5 and `velocityFlag=true` for CUST-101 fixtures; rules adds +0.15 signal | PASS | `NewEvidenceToolsTest.velocityFlagsFivePlusWithinTenMinutes` |
| Confirmed synthetic screening match | RULES/tool test | Mandatory escalation, not autonomous action | `sanctions_screening` hit for CUST-104/GLOBAL WIRE → `mandatoryEscalation`; rules forces `ESCALATE_FRAUD_OPS`/URGENT with explicit reason | PASS | `NewEvidenceToolsTest.screeningHitMandatesEscalation`, `RulesAnalyzer` |
| Evidence service unavailable (TXN-9999) | SPRING_AI / RULES | Insufficient evidence / request info | rules: `REQUEST_EVIDENCE` + specific requests; model mode: escalation with evidence requests | PASS | `GoldenEvaluationTest.missingTransactionRequestsEvidence`; FC-1005 |
| Same case submitted twice | SPRING_AI (live) | Idempotent / duplicate protection | Second intake returns **HTTP 409** `"Case FC-1001 already exists for transaction TXN-5001"`; UI shows field error | PASS | live 409 verified; `IntakeDeduplicationTest` |
| Restart then intake (collision regression) | live | No 500s; unique case numbers | After restart the next case was **FC-1009** (counter seeded from persisted max); no collisions | PASS | live verification post-restart |
| Policy question with active source | SPRING_AI | Grounded answer + title/section/version | Retrieved passages injected; grounded citations with policyId/version/section/title/snippet | PASS | FC-1003/FC-1008 citations |
| Question absent from corpus | (test) | Explicit no-information | Zero-vocabulary queries return insufficient → abstention → `MANUAL_REVIEW` | PASS | `VectorPolicyRetrieverTest.abstainsWhenNothingMatches` |
| Retired policy with matching text | (test) | Retired excluded | `POL-LEGACY-099` (RETIRED, deliberately on-topic) is never indexed/cited | PASS | `RetiredPolicyExclusionTest` |
| Model returns malformed output | (code+tests) | Validated retry or safe failure | Extract → schema validate → 1 retry → `RULES_FALLBACK` + traced step | PASS | `AbstractAiFraudCaseProcessor.analyze`, `AiFallbackTest` |
| Two separate case IDs | (test) | Isolated memory | Both framework memory contracts return only the case's own turns; window bounded to 10 | PASS | `CaseMemoryIsolationTest` |
| Prompt injection in transaction narrative | SPRING_AI | Narrative treated as data | Injected route demand ignored → `RECOMMEND_DENIAL`/LOW; prompts now also carry explicit untrusted-data delimiters | PASS | FC-1006 live + `PromptFactory` |

## 7. Strengths
1. **Real safety architecture, not prompt promises**: deterministic safety net forces `MANUAL_REVIEW` on ungrounded/abstained output; schema contract validated pre-persistence with retry → `RULES_FALLBACK`; all traced — and now automatically regression-tested.
2. **Genuine dual-framework implementation with verified parity**: identical route/priority/citations on the same case; both adapters now also use framework-native chat memory over the same JPA store.
3. **Observable and auditable by construction**: every plan, tool call (incl. REJECTED), retrieval, model call, validation and decision is a persisted `InvestigationStep`; append-only audit; Actuator health/metrics.
4. **Human-in-the-loop enforced**: reviewer role required, mandatory rationale, agent only recommends — including on sanctions hits, which mandate escalation rather than action.
5. **Deterministic, testable evidence layer**: 8 bounded tools over fixtures with per-tool unit tests; embedding retrieval reproducible offline.

## 8. Gaps and Risks
| Priority | Gap or Risk | Impact | Evidence | Recommended Fix |
|---|---|---|---|---|
| Medium | Embeddings are deterministic lexical vectors, not neural | Paraphrase robustness bounded vs a neural embedder | `PolicyEmbeddingModel` | Swap in a real embedding endpoint (or ONNX/on-device embedder) behind the same `EmbeddingModel` interface |
| Medium | No automated live-model parity test; AI paths covered by fallback injection + manual live runs | Model-behavior regressions still need manual verification | `src/test/` | Add mock-`ChatModel` tests asserting identical contract output for both adapters |
| Medium | Framework tool-calling APIs unused (`@Tool`/tool callbacks, AI Services, `entity()`) — orchestrated plans instead | Less idiomatic than reference implementations, though arguably safer | processor `callModel` methods | Documented trade-off; could adopt callback-based planning later |
| Low | Hardcoded demo password defaults; CSRF exempt on `/api/**`; audit append-only enforced at JPA level only | Hygiene; acceptable for synthetic demo | `SecurityConfig`, `application.yml` | Require env passwords in prod profile; narrow CSRF exemption; DB-level audit constraints |
| Low | Duplicate `org.json` in test scope; no CI pipeline; no token-usage capture | Hygiene/observability | `pom.xml` | Exclude android-json; add CI; record usage from `ChatResponse` |

## 9. Security and Responsible AI Findings
| Severity | Finding | File or Component | Remediation |
|---|---|---|---|
| Info | `.env` correctly gitignored and untracked; no API keys/tokens in git-tracked files (grep-verified) | `.gitignore`, `git ls-files` scan | None — verified clean |
| Info | Runtime API key lives only in local `.env`/env vars; never logged or persisted | `.env` (untracked) | None |
| Info | No autonomous adverse actions exist anywhere — tools are read-only by construction; recommendations advisory; sanctions hits mandate human escalation | `EvidenceTool`, `RulesAnalyzer` | None |
| Info | Prompt-injection defense-in-depth: untrusted sections delimited + system-level data-not-instructions rule; live injection attempt held | `PromptFactory` | None — implemented this pass |
| Low | Seeded demo passwords present as hardcoded config fallbacks (env-overridable) | `FraudProperties.Security`, `application.yml` | Keep demo-only; require env vars in production |
| Low | CSRF protection disabled for `/api/**` (HTTP Basic JSON API) | `SecurityConfig` | Narrow exemption or token auth |
| Low | Audit append-only enforced at JPA entity level; raw SQL could modify rows | `AuditEvent` | DB-level constraints in production |

## 10. Recommended Improvements
### Critical
1. ~~`caseCounter` restart collision~~ — **DONE**: counter seeds from persisted max; verified live (post-restart intake → FC-1009, no 500s).
### High
2. ~~Intake dedup on `transactionRef`~~ — **DONE**: 409 + clear message (live-verified), UI field error, `DataIntegrityViolationException` → 409.
3. ~~Policy status metadata + retired exclusion~~ — **DONE**: `status`/`effectiveDate` in manifest + entity; `POL-LEGACY-099` retired fixture; index-time exclusion + tests.
4. ~~Velocity / deviation / screening tools~~ — **DONE**: 3 tools + fixtures + rules signals + unit tests.
5. ~~Real embedding store~~ — **DONE**: LC4J `EmbeddingModel` + `InMemoryEmbeddingStore` + `EmbeddingSearchRequest`.
### Medium
6. ~~Framework chat memory~~ — **DONE**: Spring AI `MessageWindowChatMemory`/JPA `ChatMemoryRepository`, LC4J `MessageWindowChatMemory`/JPA `ChatMemoryStore`, case-id isolation, window 10.
7. ~~Prompt-injection hardening~~ — **DONE**: untrusted-data delimiters + explicit rule.
8. ~~Actuator + JaCoCo~~ — **DONE**: `/actuator/health|info|metrics` live-verified; coverage report generated.
9. Remaining: neural embedding model behind the same interface; mock-model automated parity test; CI pipeline.
### Low
10. Conflicting-policy retrieval test; token-usage capture; env-required passwords; narrower CSRF.

## 11. Definition-of-Done Checklist
- [x] Fraud case intake works with synthetic data (UI + `POST /api/cases`; verified live)
- [x] Deterministic tools provide traceable evidence (8 tools, persisted `EvidenceRecord`s, budgeted executor)
- [x] Structured recommendations are validated (`recommendation.schema.json`, retry, fallback)
- [x] Policy answers are grounded and cited (retrieved passages only; ungrounded citations dropped; retired excluded)
- [x] Human review is mandatory (REVIEWER role, mandatory rationale, disposition closes case)
- [x] Conversation/case memory is isolated (framework memory contracts, case-id scoped, tested)
- [x] Secrets are not stored in the repository (`.env` untracked; grep scan clean)
- [x] Error and no-information states are handled safely (abstention → MANUAL_REVIEW; tool errors → ERROR; duplicates → 409; restarts no longer collide)
- [x] Automated tests pass (46/46)
- [x] README enables another participant to run the project (updated for new tools, memory, embeddings, actuator)

## 12. Final Verdict
**Demo Ready.**

Every previously identified Critical and High gap is closed and verified: intake against a restarted persisted database now produces the next sequential case number (FC-1009 observed) instead of HTTP 500 collisions; duplicate transaction references return a clean 409; retrieval runs on a real framework `EmbeddingStore` with lifecycle metadata and tested retired-policy exclusion; the tool catalog covers velocity, deviation and sanctions/PEP screening; both AI adapters use framework-native bounded chat memory; prompts carry explicit untrusted-data hardening; and the test suite grew from 29 to 46 all-green tests including fallback, boundary, isolation and exclusion coverage.

The remaining deductions are honest scope choices rather than defects: embeddings are deterministic lexical vectors rather than a neural model; framework tool-callback/AI-Services APIs are unused in favor of an orchestrated (and fully traced) plan executor; and live-model parity is verified manually rather than by an automated mock-model test. The agent remains strictly advisory — every recommendation requires a human disposition.
