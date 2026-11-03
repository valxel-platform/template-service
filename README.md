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
| Security | OAuth 2.0 resource server (JWT), stateless, deny by default; only health probes open | Secure by default; the service refuses to start without an issuer |
| Unit tests | JUnit 6, AssertJ, MockMvc for the web layer; JaCoCo line coverage of at least 90% | Fast feedback; the floor the platform requires (VX-11) |
| Contract tests | Pact provider verification (`./gradlew contractTest`) | Consumers can't be broken silently |
| Deployed checks | `DeployedSmokeTest` (tag `deployed`) against `BASE_URL` | Integration in dev, end-to-end in staging, smoke in production |
| Observability | Actuator liveness and readiness, Prometheus metrics, ECS JSON logs | Platform probes and dashboards work without extra setup |
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

`post-deploy.yml` runs when the pipeline sends a `vrixel-deployed` repository dispatch after a
deployment is ready (or by hand with **Run workflow**).

## Develop

```bash
./gradlew test                 # unit tests and coverage (build/reports/jacoco)
./gradlew contractTest         # verify consumer pacts in src/test/resources/pacts
./gradlew check                # everything CI runs
./gradlew bootRun --args='--app.security.enabled=false'   # local only, never in an environment
```

Deployed environments set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI` to the product's
identity provider. Secrets come from OpenChoreo secret references, never from the repo.

## After creating a repo from this template

1. Rename the package (`com.vrixel.service`), `rootProject.name` and the Pact provider name.
2. Replace the greeting example with your first endpoint, test first.
3. Point `PactProviderTest` at your consumers' pacts (`@PactBroker` once you run a broker).
4. Products that handle PHI or payments keep data out of logs and fixtures: see `SECURITY.md`.
