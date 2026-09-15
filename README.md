# apis

Spec-first API contracts (OpenAPI and AsyncAPI) — one module per bounded context, each an independently versioned,
independently publishable dependency.

**This repo is spec-only.** No code is generated or compiled here. Each module is a thin jar whose only
content is its `asyncapi.yml` (or `openapi.yml`), packaged as a plain classpath resource. Services that
depend on a module generate their own producer/consumer code from that bundled spec at their own build time.

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
├── session-api/         ← OpenAPI contract for lobby/session endpoints
│   └── src/main/resources/openapi/v1/session.yml
├── action-api/          ← OpenAPI contract for action-round endpoints
│   └── src/main/resources/openapi/v1/action.yml
├── scoring-api/         ← OpenAPI contract for scoring endpoints
│   └── src/main/resources/openapi/v1/scoring.yml
├── chains-api/          ← OpenAPI contract for timeline-service's gated Weaver chain endpoint
│   └── src/main/resources/openapi/v1/chains.yml
└── projection-api/      ← OpenAPI contract for per-player game-state projection endpoints
    └── src/main/resources/openapi/v1/projection.yml
```

OpenAPI modules version their URL path prefix as a folder under `openapi/` (`v1/`, later `v2/`, ...), never
replacing the previous version's file. The prefix is written literally into each spec's `paths:` keys (e.g.
`/api/v1/lobbies`) — `openapi-generator-maven-plugin`'s `spring` generator does not apply `servers:` to the
generated `@RequestMapping` paths, only to springdoc documentation, so `servers:` must not be relied on for
routing. See "Adding a new OpenAPI contract module" below for how a consumer selects which version(s) to
generate.

Each module publishes as `io.github.temporal-rift:{module-name}`. A consumer depends on only the contracts it
needs, not a monolithic library of every event and REST API.

## Contract versioning

Each `*-api` and `*-event` module uses Semantic Versioning independently, relative to its previously published
version:

| Change | Version increment | Examples |
|---|---:|---|
| Backward-compatible bug fix or documentation-only correction | Patch (`x.y.z+1`) | Correct a schema description without changing the generated contract shape |
| Backward-compatible addition | Minor (`x.y+1.0`) | Add an optional field, endpoint, or event |
| Backward-incompatible change | Major (`x+1.0.0`) | Remove or rename a field/event, change type or requiredness incompatibly, or alter existing semantics incompatibly |

Choose the increment based on the compatibility of the change with consumers of the previously published version.

`session-event`, `action-event`, and `scoring-event` publish to the `game.events` topic; `timeline-event`
publishes to `timeline.events`.

## Spec compatibility gate

The versioning table above is enforced by CI, not just documented. The `spec-compat` job in
`.github/workflows/spec-compat.yml` runs `scripts/check_spec_compat.py` on every pull request: for each
module whose spec changed relative to the PR base, it structurally diffs the base and head specs (operations,
messages, paths, schema properties, `required` sets, types, formats, enums, and constraints — local and
`shared/` `$ref`s resolved, descriptions and comments ignored) and requires a matching version bump in that
module's `pom.xml`:

| Spec change | Required bump | Examples |
|---|---|---|
| Backward-incompatible (remove/rename a field, event, message, path, or operation; change a type, format, enum membership, or requiredness; tighten a constraint) | Major | Drop a payload property, rename a message, make an optional field required |
| Backward-compatible addition (new optional field, endpoint, event, or loosened constraint branch) | At least minor | Add an optional property, a new path, or a new message |
| Documentation-only or loosening edit | Any (including none) | Reword a description, make a required field optional |
| No spec change | None required (downgrades still fail) | Java test or workflow edits |

Notes:

- Changes to `shared-schemas/` count as spec changes for every module that packages them (all `*-event`
  modules and any `*-api` module whose `pom.xml` bundles `../shared-schemas`).
- Anything the classifier cannot prove compatible (unresolvable reference, unknown constraint change) fails
  closed as breaking — bump major or restructure the edit.
- To run the same check locally before pushing: `pip install pyyaml`, then
  `python scripts/check_spec_compat.py --base origin/main`. Classifier unit tests live in `scripts/tests/`
  (`python -m unittest discover -s scripts/tests -v`).

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
   This is a plain relative-path JSON Reference, resolved against the packaged jar's own
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
2. Place the OpenAPI spec under `src/main/resources/openapi/v1/`. The resource path is part of the consumer build
   contract, so use a stable module-specific filename (`v1/{name}.yml`).
3. Write every operation's `paths:` key with the version prefix included literally (e.g. `/api/v1/lobbies`), and
   set `servers: - url: /` — the prefix must live in `paths:`, not `servers:`, because the generator ignores
   `servers:` for routing.
4. Add the module to the root `<modules>` list.
5. Run `mvn package` (never `mvn install`) and inspect the jar to confirm that the OpenAPI resource is bundled
   under `openapi/v1/`.
6. Publish the module through the normal `main`-branch release workflow before changing a consumer to depend on it.

Consumers must unpack the bundled resource during Maven's lifecycle before invoking OpenAPI Generator. They must not
copy the spec into the consuming repository or depend on a sibling checkout.

### Adding a second coexisting version (`v2`, ...)

Add `src/main/resources/openapi/v2/{name}.yml` alongside the existing `v1/{name}.yml` — never replace or move the
prior version's file; both are published together in the same module release. A consuming service chooses which
version(s) to generate by pointing a generator execution's `inputSpec` at the extracted `v{n}` resource and giving
that execution's `apiPackage`/`modelPackage` a matching `.v{n}` suffix (see `temporal-rift-bom`'s README for the
extraction/unpack mechanics). A service that only wants `v1` simply never adds a `v2` execution; a service
migrating between versions can run both executions at once so the two generated interfaces coexist without
collision.

## Publishing

`.github/workflows/publish.yml` discovers every top-level module automatically, checks whether its current
`pom.xml` version is already on Maven Central, and publishes only the ones that changed — each module tagged
and versioned independently (`{module}/v{version}`), triggered on push to `main`.

Requires these repo secrets configured before merging any change to the publish workflow — the workflow will
succeed at CI level but do nothing meaningful without them:

- `SONATYPE_USERNAME` / `SONATYPE_PASSWORD` — Central Publisher Portal token.
- `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` — signs artifacts (required by Central).
