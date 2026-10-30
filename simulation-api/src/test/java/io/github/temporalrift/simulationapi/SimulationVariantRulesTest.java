package io.github.temporalrift.simulationapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;

/** Pins variants as a baseline rules document plus parameter overrides and the effective rules contract. */
class SimulationVariantRulesTest {

    private static final String SPECIFICATION = "openapi/v1/simulation.yml";
    private static final String DIGEST = "a".repeat(64);
    private static final String ID = "6f3f2a52-4f0a-4c69-9a39-2a8f0e2b9d11";
    private final String url = Objects.requireNonNull(getClass().getClassLoader().getResource(SPECIFICATION))
            .toString();
    private final OpenAPI openApi = new OpenAPIV3Parser().read(url);
    private final OpenApiInteractionValidator validator =
            OpenApiInteractionValidator.createForSpecificationUrl(url).build();

    @Test
    void aVariantWithoutOverridesValidatesUnchanged() {
        assertValid(validator.validateRequest(createExperiment(variant("threshold-20", ""))));
    }

    @Test
    void nonEmptyOverridesRequireAParameterSpaceVersion() {
        var override = ", \"overrides\": { \"game.rules.win-score-threshold\": 22 }";

        assertFalse(validator
                .validateRequest(createExperiment(variant("threshold-22", override)))
                .getMessages()
                .isEmpty());
        assertValid(validator.validateRequest(
                createExperiment(variant("threshold-22", ", \"parameterSpaceVersion\": 1" + override))));
    }

    @Test
    void parameterSpacesAreServedInVersionOrder() {
        var catalog = """
                {
                  "items": [
                    { "version": 1,
                      "parameters": [
                        { "key": "game.rules.win-score-threshold", "services": ["game-service"],
                          "baseline": 20, "min": 10, "max": 30, "step": 1 }
                      ],
                      "derived": [
                        { "key": "game.rules.probability.amplify-multiplier.III",
                          "from": "game.rules.probability.amplify-multiplier.II", "factor": 2 }
                      ],
                      "constraints": [
                        { "id": "threshold-below-max", "expression": "win-score-threshold < 30",
                          "description": "The win threshold must stay below the maximum." }
                      ],
                      "heldFixed": [
                        { "key": "game.rules.max-eras", "reason": "Capped by the saga status enum." }
                      ] }
                  ]
                }
                """;

        assertValid(validator.validateResponse("/api/v1/parameter-spaces", Request.Method.GET, json(catalog)));
        assertValid(validator.validateResponse(
                "/api/v1/parameter-spaces/1", Request.Method.GET, json("""
                        { "version": 1, "parameters": [], "derived": [], "constraints": [], "heldFixed": [] }
                        """)));

        assertTrue(openApi.getPaths().get("/api/v1/parameter-spaces/{version}").getGet()
                .getResponses().containsKey("404"));
    }

    @Test
    void aVariantRulesPreviewValidatesItsRequestAndResponse() {
        var request = """
                {
                  "rulesArtifact": { "digest": "%s", "name": "baseline" },
                  "parameterSpaceVersion": 1,
                  "overrides": { "game.rules.win-score-threshold": 22 }
                }
                """.formatted(DIGEST);

        assertValid(validator.validateRequest(preview(request)));

        var response = """
                {
                  "rules": {
                    "schemaVersion": 1,
                    "properties": {
                      "game.rules.win-score-threshold": 22,
                      "game.rules.probability.amplify-multiplier.II": 2
                    }
                  },
                  "effectiveRulesDigest": "%s",
                  "overrides": { "game.rules.win-score-threshold": 22 }
                }
                """.formatted(DIGEST);

        assertValid(validator.validateResponse("/api/v1/variant-rules-previews", Request.Method.POST, json(response)));
        var withoutOverrides = response.replace(
                ",\n  \"overrides\": { \"game.rules.win-score-threshold\": 22 }", "");
        assertFalse(withoutOverrides.contains("overrides"));
        var report = validator.validateResponse(
                "/api/v1/variant-rules-previews", Request.Method.POST, json(withoutOverrides));
        assertTrue(
                report.getMessages().stream()
                        .anyMatch(message -> message.getKey().equals(
                                "validation.response.body.schema.required")),
                report::toString);
    }

    @Test
    void anExplicitlyEmptyOverridesMapNeedsNoParameterSpaceVersion() {
        assertValid(validator.validateRequest(createExperiment(variant("baseline", ", \"overrides\": {}"))));
    }

    @Test
    void frozenVariantRulesAreReadableAndRefusalsNameTheirReason() {
        var rules = """
                { "schemaVersion": 1,
                  "properties": { "game.rules.win-score-threshold": 22 } }
                """;

        assertValid(validator.validateResponse(
                "/api/v1/experiments/" + ID + "/variants/threshold-22/rules", Request.Method.GET, json(rules)));

        var refusal = """
                { "type": "about:blank", "title": "Bad Request", "status": 400,
                  "detail": "override is outside its range", "code": "INVALID_REQUEST",
                  "reason": "OUT_OF_RANGE",
                  "field": "variants[1].overrides.game.rules.win-score-threshold" }
                """;

        assertValid(validator.validateResponse(
                "/api/v1/variant-rules-previews", Request.Method.POST, problemJson(400, refusal)));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/variant-rules-previews",
                        Request.Method.POST,
                        problemJson(400, refusal.replace("OUT_OF_RANGE", "OUT_OF_BOUNDS")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aConstraintViolationCarriesItsIdentifier() {
        var refusal = """
                { "type": "about:blank", "title": "Bad Request", "status": 400,
                  "detail": "the effective rules break a constraint", "code": "INVALID_EXPERIMENT",
                  "reason": "CONSTRAINT_VIOLATED", "constraintId": "threshold-below-max",
                  "field": "variants[0].overrides.game.rules.win-score-threshold" }
                """;

        assertValid(validator.validateResponse(
                "/api/v1/variant-rules-previews", Request.Method.POST, problemJson(400, refusal)));
        var reason = (Schema<?>) openApi.getComponents().getSchemas().get("ProblemDetail")
                .getProperties().get("reason");
        assertEquals(
                List.of(
                        "UNKNOWN_PARAMETER_SPACE",
                        "UNKNOWN_BASELINE",
                        "UNKNOWN_PARAMETER",
                        "OUT_OF_RANGE",
                        "OFF_STEP",
                        "CONSTRAINT_VIOLATED",
                        "UNKNOWN_CRITERION",
                        "EQUAL_MASTER_SEEDS",
                        "INVALID_VALUE"),
                reason.getEnum());
    }

    @Test
    void effectiveRulesCarryTheirProperties() {
        assertTrue(openApi.getComponents().getSchemas().get("EffectiveRules")
                .getProperties().containsKey("properties"));
    }

    private Request createExperiment(String variants) {
        var manifest = """
                {
                  "schemaVersion": 1,
                  "name": "variant overrides",
                  "variants": [ %2$s ],
                  "services": {
                    "gameService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" },
                    "timelineService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" },
                    "readService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" }
                  },
                  "contracts": { "simulation-api": "3.4.0" },
                  "policies": [ {
                    "id": "random", "version": "v1", "artifactDigest": "%1$s", "parameters": {},
                    "seatAssignment": [ { "seatIndex": 0, "policyId": "random", "policyVersion": "v1" } ]
                  } ],
                  "seeds": ["42"],
                  "playerCounts": [3],
                  "factionSets": [["ERASERS", "ACTIVISTS", "WEAVERS"]],
                  "seatRotationMode": "CYCLIC",
                  "timingMode": "LOGICAL",
                  "concurrency": 1,
                  "caseWallTimeoutSeconds": 60,
                  "maxRejectedCandidatesPerWindow": 10
                }
                """.formatted(DIGEST, variants);

        return SimpleRequest.Builder.post("/api/v1/experiments")
                .withHeader("Authorization", "Bearer token")
                .withHeader("Idempotency-Key", "3f2b1c0d-9e8f-4a7b-8c6d-5e4f3a2b1c0d")
                .withContentType("application/json")
                .withBody(manifest)
                .build();
    }

    private static String variant(String label, String extra) {
        return """
                {
                  "label": "%s",
                  "rulesArtifact": { "digest": "%s" },
                  "contentArtifact": { "digest": "%s" },
                  "effectiveRulesDigest": "%s",
                  "effectiveContentDigest": "%s"%s
                }
                """.formatted(label, DIGEST, DIGEST, DIGEST, DIGEST, extra);
    }

    private static Request preview(String body) {
        return SimpleRequest.Builder.post("/api/v1/variant-rules-previews")
                .withHeader("Authorization", "Bearer token")
                .withContentType("application/json")
                .withBody(body)
                .build();
    }

    private static SimpleResponse json(String body) {
        return SimpleResponse.Builder.ok()
                .withContentType("application/json")
                .withBody(body)
                .build();
    }

    private static SimpleResponse problemJson(int status, String body) {
        return new SimpleResponse.Builder(status)
                .withContentType("application/problem+json")
                .withBody(body)
                .build();
    }

    private static void assertValid(ValidationReport report) {
        assertTrue(report.getMessages().isEmpty(), report::toString);
    }
}
