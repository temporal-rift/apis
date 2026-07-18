# apis

Spec-first API contracts (AsyncAPI today, OpenAPI later) — one module per bounded context, each an
independently versioned, independently publishable dependency. Replaces `domain-events`.

**This repo is spec-only.** No code is generated or compiled here. Each module is a thin jar whose only
content is its `asyncapi.yml` (or `openapi.yml`), packaged as a plain classpath resource. Services that
depend on a module generate their own producer/consumer code from that bundled spec at their own build time,
via the `zenwave-sdk-maven-plugin` centralized in `temporal-rift-bom`'s `pluginManagement` — see
`temporal-rift-bom`'s README/pom for the plugin identity, and a consuming service's `pom.xml` for the
per-spec `<execution>` (role, inputSpec, target packages). Nothing about codegen belongs in this repo.

## Structure

```
apis/
├── pom.xml              ← parent: shared build config (source/javadoc jars, central-publish profile)
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

1. Copy `session-event/pom.xml` as a starting point (parent block, packaging, description). No plugins
   needed — this module's pom just needs its own artifactId/version and the spec resource.
2. Write the spec at `src/main/resources/asyncapi/asyncapi.yml`. Model messages with `headers` and `payload`
   as separate schemas — `headers` carries envelope metadata (`eventId`, `aggregateId`, `aggregateType`,
   `gameId`, `occurredAt`, `version`), `payload` carries the event's own fields. This maps directly onto how
   consuming services' outbox envelope already works.
3. Add the new module to `<modules>` in the root `pom.xml`.
4. `mvn package` (never `mvn install`) to confirm the jar builds and contains the resource before committing.

Consuming-side codegen notes (role config, header typing, transactional outbox wiring) live in
`temporal-rift-bom`, not here — see its README.

## Publishing

`.github/workflows/publish.yml` discovers every top-level module automatically, checks whether its current
`pom.xml` version is already on Maven Central, and publishes only the ones that changed — each module tagged
and versioned independently (`{module}/v{version}`), triggered on push to `main`.

Requires these repo secrets configured before merging any change to the publish workflow — the workflow will
succeed at CI level but do nothing meaningful without them:

- `SONATYPE_USERNAME` / `SONATYPE_PASSWORD` — Central Publisher Portal token.
- `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` — signs artifacts (required by Central).
