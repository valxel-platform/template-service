# Service template (Java 21, Spring Boot 4)

The golden path for Vrixel API services. The Vrixel SDLC creates product repos from this template
when an approved design needs a new service; you can also use **Use this template** on GitHub.
Everything below is wired to the platform's gates, so a new repo is deliverable from its first PR.

## What you get

| Area | Choice | Why |
| --- | --- | --- |
| Runtime | Java 21, Spring Boot 4.1, virtual threads | LTS Java; current Boot line; cheap concurrency for I/O-bound APIs |
| Build | Gradle 9.8 wrapper, version catalog, configuration cache | Reproducible builds; one place for versions |
| API | Versioned `/api/v1`, OpenAPI 3.1 in `api/openapi.yaml`, RFC 9457 problem details | Contract-first; errors never echo request data |
| Security | Tokens from the product's Keycloak realm, issued for this product (`APP_AUDIENCE`); `@PreAuthorize` on every endpoint (a test fails the build otherwise); record-level `AccessPolicy` (tenants apart by default); token forward or exchange for outgoing calls | VX-17: who, what and which record on every request; the service refuses to start without issuer and audience |
| Unit tests | JUnit 6, AssertJ, MockMvc for the web layer; JaCoCo line coverage of at least 90% | Fast feedback; the floor the platform requires (VX-11) |
| Contract tests | Pact provider verification (`./gradlew contractTest`) | Consumers can't be broken silently |
| Deployed checks | `DeployedSmokeTest` (tag `deployed`) against `BASE_URL` | Integration in dev, end-to-end in staging, smoke in production |
| Feature flags | OpenFeature with an OFREP provider for the product's Flipt (`FLAGS_URL`); `Flags.enabled(key, user)` | New behavior ships dark; the SDLC tool creates the flag and turns it on at release |
| Observability | Actuator liveness and readiness, Prometheus metrics, ECS JSON logs with trace ids, OpenTelemetry traces (`spring-boot-starter-opentelemetry`) exported to the data plane's collector | Platform probes and dashboards work without extra setup; one request can be followed across services |
| Product events | `ProductEvents.record("checkin_completed")`: a counter (`vrixel.events`), a summary line a minute and per-flag counts | The SDLC tool proves in staging that a feature's goal events fire, reads goals from them after release, and compares users with the feature on against off |
| Events | Commands publish one CloudEvent to Kafka (`Events.publish`) and answer 202; event workers keep the data stores up to date. Kafka down means 503 and no change | No dual writes (VX-16): Kafka is the record, and every database can be rebuilt from it |
| Audit | `Auditor` publishes who did what to which records (CloudEvents `vrixel.audit.v1`) to the product's Kafka audit topic; if a record can't be published the request fails with 503 | Access to PHI and payments data is on record, in an append-only, hash-chained store with an object-storage backup (ss-audit-sink) |
| Image | Multi-stage Dockerfile, Boot layers, distroless JRE, non-root | Small, cache-friendly, minimal attack surface |
| Supply chain | Actions pinned by commit SHA, Gradle wrapper validation, dependency graph submission, dependency review, Dependabot | Known inputs; vulnerable dependencies are caught on the PR |

## Checks and how the pipeline reads them

| Check | Where | Required | Read by the Vrixel SDLC as |
| --- | --- | --- | --- |
| `unit` | `ci.yml` | yes | the `unit` suite, with counts and coverage from `unit / report` |
| `contract` | `ci.yml` | yes | the `contract` suite (`contract / report`) |
| `CodeQL` | default setup, enabled at provisioning | yes | the `sast` suite |
| `dependency-review` | `ci.yml` | no | the `dependencies` suite reads GitHub's dependency diff |
| `image` | `ci.yml` | no | proves the Dockerfile builds; OpenChoreo builds the shipped image |
| `integration / report`, `e2e / report`, `smoke / report` | `post-deploy.yml` | per stage | the `integration`, `e2e` and `smoke` suites |
| `perf-smoke / report` | `perf.yml` | when the change needs it | the `perf-smoke` suite: this PR's image against main's |
| `performance / report` | `post-deploy.yml` (staging) | when the change needs it | the `performance` suite: the staging load test |

`post-deploy.yml` runs when the pipeline sends a `vrixel-deployed` repository dispatch after a
deployment is ready (or by hand with **Run workflow**).

## Performance tests

The Vrixel SDLC decides per change whether this repo needs a performance test: a hot path, a
migration, an upgrade or settings change gets the **smoke** test; an API contract change or a
design that adds traffic gets the **staging load test** as well. It labels the pull request
(`vrixel:perf-smoke`, `vrixel:perf-full`) and opens the report as pending; the workflows answer.

- **Smoke** (`perf.yml`, `perf/smoke.js`): the image built from main and the PR's image, side by
  side on one runner, 30 requests a second for a minute after a warm-up. Fails when the PR is more
  than 20% slower than main at the 95th or 99th percentile, its error rate rises, or it misses the
  budget.
- **Staging load** (`post-deploy.yml`, `perf/load.js`): the product's expected peak scaled to
  staging (`peak_rps` x `staging_load_factor`, 10% by default) for three minutes, reads and writes
  mixed, with `API_TOKEN`'s test identity and synthetic records. Judged against the budget and the
  last passing run, which the job keeps on the `perf-baselines` branch.
- **Budgets**: 500 ms at the 95th percentile, 1 s at the 99th, under 1% errors, unless the
  product's `performance:` settings say otherwise. `perf/compare.py` judges, and writes a table to
  the run's summary.

Replace the requests in `perf/*.js` with your API's hot paths as it grows. Run locally with
`docker run --rm --network host -v $PWD/perf:/perf grafana/k6:2.3.0 run /perf/smoke.js`.

## Develop

```bash
./gradlew test                 # unit tests and coverage (build/reports/jacoco)
./gradlew contractTest         # verify consumer pacts in src/test/resources/pacts
./gradlew check                # everything CI runs
./gradlew bootRun --args='--app.security.enabled=false'   # local only, never in an environment
```

Deployed environments set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI` to the product's
identity provider. Secrets come from OpenChoreo secret references, never from the repo.

## Security

Every request is checked for **who**, **what** and **which record** (`com.vrixel.service.security`):

1. **Who.** A JWT from the product's Keycloak realm (`APP_ISSUER`), checked against the realm's
   published keys (rotation is picked up by `kid`), not expired, and issued for this product:
   `aud` must contain `APP_AUDIENCE` (e.g. `emr-api`). A token for another product is refused.
2. **What.** Every endpoint declares its permission:
   `@PreAuthorize("hasAuthority('SCOPE_greetings:write')")`. Scopes become `SCOPE_*`, Keycloak realm
   roles `ROLE_*`; a permission scope counts only if the person also holds it (the realm's
   `permissions` claim), so what the app asked for and what the person may do must both allow it. Paths outside `/api` are denied, and `EndpointPermissionsTest` fails the build
   for an endpoint without `@PreAuthorize`.
3. **Which record.** `AccessPolicy.require(caller, new Resource(type, id, tenant), action)` in every
   handler that touches a record. The default keeps tenants apart (`APP_TENANT_CLAIM`, Keycloak's
   `organization`); replace the bean to ask OpenFGA or OPA for relationship rules. Refusals are 403
   with no detail.

Audit records name the user (`sub`) and the client acting for them (`azp`).

**Calling other services** (`app.outbound`, `BearerFor`):

| Target | Mode | Why |
| --- | --- | --- |
| Same product | `forward` | The mesh (Istio ambient mTLS) proves which service calls; the token proves which user |
| Another product, a shared service, PHI stores, payments | `exchange` | RFC 8693 at Keycloak: a token for that service only, with only the scopes needed; cached per caller and target |

Exchange authenticates with `private_key_jwt` (`client-key-file`) rather than a shared secret when
it can. There is no fallback to the service's own identity on a user's behalf.

**Exposure.** `workload.yaml` declares one endpoint, `api`, visible to its project. Health and
metrics are on the management port (`MANAGEMENT_PORT`, 8081), which no endpoint exposes.

## Feature flags

The Vrixel SDLC creates a flag for each feature in the product's Flipt when its design is
approved (on in dev and staging, off in production until release). Put the new behavior behind it:

```java
if (flags.enabled("emr.emr-feat-3.check-in-kiosk", user)) {
    // the new behavior
} else {
    // today's behavior
}
```

`com.vrixel.service.flags` reads flags over OFREP from the product's Flipt. Set per environment:
`FLAGS_URL` (e.g. `http://flipt.emr.svc:8080`), `FLAGS_ENVIRONMENT` (e.g. `production`),
`FLAGS_NAMESPACE` (e.g. `emr`) and, if the product's Flipt requires it for evaluation,
`FLAGS_TOKEN`. Without `FLAGS_URL`, or when Flipt can't be reached, every flag reads as off. Each
answer is reused for `FLAGS_CACHE_SECONDS` (30) per flag and user, and a call to Flipt waits at most
250 ms, so most requests don't call Flipt and a slow Flipt never slows a request by more. Tests
set flags with OpenFeature's `InMemoryProvider` (see `FlagsTest`).

## Product events: whether a feature moves its goal

Each feature's PRD names the product goal it moves and the events that show it (its
Instrumentation section); the HLD says which repo records each. Record them where they happen:

```java
ProductEvents.record("checkin_completed");
ProductEvents.record("checkin_completed", Map.of("visit_type", "follow_up")); // categories only
```

`com.vrixel.service.outcomes.ProductEvents` sends each event three ways, all counts:

| Where | What | The SDLC tool uses it to |
| --- | --- | --- |
| Counter `vrixel.events`, attribute `event` | Prometheus `vrixel_events_total{event="checkin_completed"}` | read a goal's metric (`sum(increase(vrixel_events_total{event="checkin_completed"}[$window]))`) |
| One log line a minute | `vrixel.events {"checkin_completed":12}` | prove every event the PRD names fires in staging before G6 |
| The request's flag cohorts | `"events":{"checkin_completed":9}` on each side of `vrixel.flag_cohorts` | compare how often users with a feature on do it against users with it off during a gradual release |

Names are lowercase words joined by `_` or `.`. Attributes are low-cardinality categories (a visit
type, a plan): an attribute that names or identifies a person (a name, contact details, an id such
as `patient_id` or `user_id`) throws, as does a name in any other form. Tests use
`ProductEvents.counting()` and `drain()` (see `ProductEventsTest`).

## Changing state: Kafka first

A request that changes state doesn't write a database. It checks the command, publishes one event
saying what changed, and answers 202 Accepted once Kafka holds it on every in-sync replica
(`com.vrixel.service.events`, example: `PUT /api/v1/greetings/{id}`):

```java
Events.Published p = events.publish("patients", "registered", patientId, data, idempotencyKey);
return ResponseEntity.accepted().body(Accepted.of(p.eventId()));
```

- Topic `<prefix>.<entity>.v1` (`EVENTS_TOPIC_PREFIX`, e.g. `emr.intake-api`), keyed by the record's
  id so its changes stay in order; type `vrixel.<entity>.<what happened>.v1`.
- Kafka doesn't confirm in time (`EVENTS_TIMEOUT_MS`): 503, nothing changed, and a `failure` audit
  record follows the change's record. Retries with the same `Idempotency-Key` from the same caller
  and tenant get the same event id, so workers apply them once; another caller's key never matches.
- The data stores (Postgres, a search index, a cache) are kept by event workers, from
  `vrixel-tools/template-event-worker`, and can be rebuilt by replaying the topic. Reads come from
  them, so a change shows up once a worker has applied it, usually within a second.
- Never write to a database and to Kafka in the same request (constitution VX-16).

Settings: `EVENTS_SINK` (`kafka` in deployed environments, `log` locally), `EVENTS_KAFKA_BOOTSTRAP`,
`EVENTS_TOPIC_PREFIX`, `EVENTS_SOURCE`; extra Kafka client settings go under `app.events.kafka`.

## Audit and tracing

Record every read and change of product data with the `Auditor` (`com.vrixel.service.audit`):

```java
auditor.record("patient.read", Actor.user(jwt.getSubject()), "patient", List.of(patientId));
```

Records hold ids, never field values. Settings: `AUDIT_SINK` (`kafka` in deployed environments,
`log` locally), `AUDIT_KAFKA_BOOTSTRAP`, `AUDIT_TOPIC`, `AUDIT_REQUIRED` (default `true`),
`PRODUCT`, `ENVIRONMENT`, `DATA_CLASS`; extra Kafka client settings go under `app.audit.kafka`.

- **Refusals are on record.** A missing or invalid token (401) is recorded as `access.unauthenticated`
  and a missing permission or a record-level refusal (403) as `access.denied`, with the endpoint
  (ids masked) and outcome `denied`. These are sent without waiting, so a flood of bad requests
  can't slow the service down through the audit trail.
- **Required or not.** `AUDIT_REQUIRED=true` (the default, and the rule for `phi` and `payments`)
  waits for Kafka on every record and fails the request if it can't be kept. A `general` product may
  set it to `false`: records are then sent without waiting and batched, so reads don't wait on Kafka.
- **Encrypted.** A `phi` or `payments` product refuses to start unless `security.protocol` is `SSL`
  or `SASL_SSL`, for audit and events alike.

Traces export when `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` points at the data
plane's collector (e.g. `http://otel-collector:4318/v1/traces`); incoming `traceparent` headers
are honored, and logs and audit records carry the trace id. Every response carries a W3C
`traceresponse` header (`TraceResponseFilter`): the web and mobile apps put its trace id on the errors
they report, which is how the SDLC tool tells that a failure people saw and an error here are one
incident.

## Error reports

With `SENTRY_DSN` set, unhandled exceptions and `ERROR` logs go to Sentry (Spring Boot integration
and Logback appender), grouped into issues; Sentry's issue alerts reach the SDLC tool as incident
signals. `ErrorReports` keeps the exception type, stack, endpoint path and method, the log message's
pattern, a `trace_id` tag and a `component` tag (`spring.application.name`, so the SDLC tool routes
the issue to this component), and removes exception messages, formatted messages and arguments,
query strings, headers, bodies, users and breadcrumbs. Unset, Sentry is off. Also `ENVIRONMENT`
and `SENTRY_RELEASE` (the image tag). For PHI products use a Sentry plan under a BAA, or self-hosted
Sentry. Logs, traces and metrics stay in OpenChoreo.

## After creating a repo from this template

1. Rename the package (`com.vrixel.service`) and the Pact provider name. The SDLC tool has already
   put the component's name in the files `.vrixel/identity` lists (`rootProject.name`,
   `spring.application.name`, the Workload, CI); a repo created by hand needs them renamed.
2. Replace the greeting example with your first endpoint, test first.
3. Point `PactProviderTest` at your consumers' pacts (`@PactBroker` once you run a broker).
4. Products that handle PHI or payments keep data out of logs and fixtures: see `SECURITY.md`.
