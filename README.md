# apis

Spec-first API contracts (OpenAPI and AsyncAPI) — one module per bounded context, each an independently versioned,
independently publishable dependency. Replaces `domain-events`.

**This repo is spec-only.** No code is generated or compiled here. Each module is a thin jar whose only
content is its `asyncapi.yml` (or `openapi.yml`), packaged as a plain classpath resource. Services that
depend on a module generate their own producer/consumer code from that bundled spec at their own build time,
via the `zenwave-sdk-maven-plugin` centralized in `temporal-rift-bom`'s `pluginManagement` — see
`temporal-rift-bom`'s README/pom for the plugin identity, and a consuming service's `pom.xml` for the
per-spec `<execution>` (role, inputSpec, target packages). Nothing about codegen belongs in this repo.

## Structure

```text
apis/
├── pom.xml              ← parent: shared build config (source/javadoc jars, central-publish profile)
├── shared-schemas/       ← EventEnvelopeHeaders + shared enums, source of truth, not its own module
│   ├── envelope-headers.yaml
│   └── enums.yaml
├── session-event/       ← AsyncAPI contract for session events
│   ├── pom.xml           (own artifactId + version — published independently)
│   └── src/main/resources/asyncapi/asyncapi.yml
├── action-event/
├── timeline-event/
├── scoring-event/
├── session-api/         ← OpenAPI contract for game-service session endpoints
│   └── src/main/resources/openapi/session.yml
├── action-api/          ← OpenAPI contract for game-service action endpoints
│   └── src/main/resources/openapi/action.yml
├── scoring-api/         ← OpenAPI contract for game-service scoring endpoints
│   └── src/main/resources/openapi/scoring.yml
└── projection-api/      ← OpenAPI contract for read-service projection endpoints
    └── src/main/resources/openapi/projection.yml
```

Each module publishes as `io.github.temporal-rift:{module-name}`. A service consumes only the contracts it needs, not
a monolithic library of every event and REST API. For example, `game-service` consumes `session-event`,
`action-event`, and `scoring-event` for Kafka generation, plus `session-api`, `action-api`, and `scoring-api` for REST
interface generation.

`session-event`, `action-event`, and `scoring-event` publish to `game.events` (produced by `game-service`);
`timeline-event` publishes to `timeline.events` (produced by `timeline-service`, not yet built — the contract
exists ahead of the producer, same as it already did as a package in `domain-events`).

## Adding a new AsyncAPI contract module

1. Copy `session-event/pom.xml` as a starting point (parent block, packaging, description, and the
   `<build><resources>` block — that's what packages `shared-schemas/` alongside this module's own
   `src/main/resources`, at `asyncapi/shared/` inside the jar).
2. Write the spec at `src/main/resources/asyncapi/asyncapi.yml`. Model messages with `headers` and `payload`
   as separate schemas — `headers` carries envelope metadata (`eventId`, `aggregateId`, `aggregateType`,
   `gameId`, `occurredAt`, `version`), `payload` carries the event's own fields. This maps directly onto how
   consuming services' outbox envelope already works.
3. For `EventEnvelopeHeaders` and any of the shared enums (`Faction`, `CardType`, `SpecialAction`,
   `ParadoxType`, `ProbabilityBand`), don't inline the definition — reference the shared source instead:
   ```yaml
   EventEnvelopeHeaders:
     $ref: './shared/envelope-headers.yaml#/EventEnvelopeHeaders'
   Faction:
     $ref: './shared/enums.yaml#/Faction'
   ```
   This is a plain relative-path JSON Reference, resolved by ZenWave against the packaged jar's own
   `asyncapi/` directory — not a `classpath:` reference into a separate dependency, which doesn't resolve
   (verified: it fails silently and the field falls back to an untyped `Object`). Adding a new shared enum
   means editing `shared-schemas/enums.yaml` once, not touching every module that uses it.
4. Add the new module to `<modules>` in the root `pom.xml`.
5. `mvn package` (never `mvn install`) to build the jar, then inspect its contents to confirm both the spec
   and the shared schemas are actually bundled — a successful build only proves the lifecycle ran, not that
   the resources are present:
   ```bash
   jar tf {module}/target/{module}-{version}.jar | grep asyncapi
   ```

Consuming-side codegen notes (role config, header typing, transactional outbox wiring) live in
`temporal-rift-bom`, not here — see its README.

## Adding a new OpenAPI contract module

1. Copy an existing `*-api/pom.xml` and give the module its own artifact ID and version.
2. Place the OpenAPI spec under `src/main/resources/openapi/`. The resource path is part of the consumer build
   contract, so use a stable module-specific filename.
3. Add the module to the root `<modules>` list.
4. Run `mvn package` (never `mvn install`) and inspect the jar to confirm that the OpenAPI resource is bundled.
5. Publish the module through the normal `main`-branch release workflow before changing a consumer to depend on it.

Consumers must unpack the bundled resource during Maven's lifecycle before invoking OpenAPI Generator. They must not
copy the spec into the consuming repository or depend on a sibling checkout.

## Publishing

`.github/workflows/publish.yml` discovers every top-level module automatically, checks whether its current
`pom.xml` version is already on Maven Central, and publishes only the ones that changed — each module tagged
and versioned independently (`{module}/v{version}`), triggered on push to `main`.

Requires these repo secrets configured before merging any change to the publish workflow — the workflow will
succeed at CI level but do nothing meaningful without them:

- `SONATYPE_USERNAME` / `SONATYPE_PASSWORD` — Central Publisher Portal token.
- `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` — signs artifacts (required by Central).
