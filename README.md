# apis

Spec-first API contracts (OpenAPI + AsyncAPI) — one module per bounded context, each an independently
versioned, independently publishable dependency any service can add and get generated server/client code
from. Replaces `domain-events`.

## Structure

```
apis/
├── pom.xml              ← parent: shared build config, ZenWave plugin identity, central-publish profile
├── session-event/       ← one AsyncAPI spec per bounded context
│   ├── pom.xml           (own artifactId + version — published independently)
│   └── src/main/resources/asyncapi/asyncapi.yml
├── action-event/
├── timeline-event/
└── scoring-event/
```

Each module publishes as `io.github.temporal-rift:{module-name}` — e.g. a service that only needs
`timeline.events` depends on `timeline-event` alone, not a monolithic library of every event in the system.

## Adding a new bounded-context module

This took real trial and error to get right — the tool's own docs (and even its latest published version's
module names) don't match what's actually resolvable. Use this recipe, don't rediscover it:

1. Copy `session-event/pom.xml` as a starting point. Keep the `<parent>` block pointing at this repo's root
   `pom.xml` — that's what gives you the correct plugin group/artifact ids and the `central` publish profile
   for free.
2. **Exact working coordinates** (confirmed working, 2026-07-18):
   - `io.zenwave360.sdk:zenwave-sdk-maven-plugin:2.5.4` — **not** `io.github.zenwave360.zenwave-sdk` (that's
     an older, now-abandoned groupId whose last release was 1.7.1 with different, never-actually-published
     module names — don't use it, even though it's what most search results point to first).
   - Plugin dependency: `io.zenwave360.sdk.plugins:asyncapi-generator:2.5.4`.
   - `generatorName`: `AsyncAPIGenerator` (not `spring-cloud-streams3` — that's the old API).
3. **Always set `generateMessageHeaders=true`** in `configOptions`. Without it, header fields are generated
   as an untyped `HashMap<String,Object>` builder instead of a real typed POJO — e.g. a `format: uuid` header
   field silently becomes a `String` setter instead of `UUID`. With it on, headers go through the same
   `jsonschema2pojo` path as the payload and get properly typed.
4. Model your AsyncAPI message with `headers` and `payload` as separate schemas — `headers` carries the
   envelope-style metadata (`eventId`, `aggregateId`, `aggregateType`, `gameId`, `occurredAt`, `version`),
   `payload` carries the event's own fields. This maps directly onto how the consuming services' outbox
   envelope already works.
5. `transactionalOutbox=modulith` generates a producer that publishes via
   `ApplicationEventPublisher.publishEvent(...)` — the same primitive Spring Modulith's outbox interception
   already uses in the consuming services, so it drops in with no additional wiring.
6. Add the new module to `<modules>` in the root `pom.xml`.
7. `mvn generate-sources` (never `mvn install` for a first check) to verify codegen output before committing.

## Publishing

`.github/workflows/publish.yml` discovers every top-level module automatically, checks whether its current
`pom.xml` version is already on Maven Central, and publishes only the ones that changed — each module tagged
and versioned independently (`{module}/v{version}`), triggered on push to `main`.
