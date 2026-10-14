package io.github.temporalrift.simulationapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;

/** Pins the discovery operations a designer client uses to find and preview what it can act on. */
class SimulationDiscoveryTest {

    private static final String SPECIFICATION = "openapi/v1/simulation.yml";
    private static final String DIGEST = "a".repeat(64);
    private static final String ID = "6f3f2a52-4f0a-4c69-9a39-2a8f0e2b9d11";

    private final String url = Objects.requireNonNull(getClass().getClassLoader().getResource(SPECIFICATION))
            .toString();
    private final OpenAPI openApi = new OpenAPIV3Parser().read(url);
    private final OpenApiInteractionValidator validator =
            OpenApiInteractionValidator.createForSpecificationUrl(url).build();

    @Test
    void everyDiscoveryOperationDeclaresItsScope() {
        var scopes = Map.of(
                "/api/v1/experiment-previews", List.of("simulation:write"),
                "/api/v1/experiments/{experimentId}", List.of("simulation:read"),
                "/api/v1/runs", List.of("simulation:read"),
                "/api/v1/runs/{runId}/cases", List.of("simulation:read"),
                "/api/v1/policies", List.of("simulation:read"),
                "/api/v1/lane-bundles", List.of("simulation:read"),
                "/api/v1/experiment-templates", List.of("simulation:read"));

        scopes.forEach((path, expected) -> {
            var path1 = openApi.getPaths().get(path);
            var operation = path1.getPost() != null ? path1.getPost() : path1.getGet();
            assertEquals(expected, operation.getExtensions().get("x-required-scopes"), path);
        });
        assertEquals(
                List.of("simulation:read"),
                openApi.getPaths().get("/api/v1/experiments").getGet().getExtensions().get("x-required-scopes"));
        assertEquals(
                List.of("simulation:read"),
                openApi.getPaths().get("/api/v1/comparisons").getGet().getExtensions().get("x-required-scopes"));
    }

    @Test
    void anExperimentPreviewCarriesTheDigestTheCountAndAPageOfCases() {
        var preview = """
                {
                  "manifestDigest": "%s",
                  "caseCount": 55,
                  "breakdown": [ { "variantLabel": "threshold-20", "playerCount": 3, "caseCount": 30 } ],
                  "cases": [ {
                    "caseKey": "%s", "variantLabel": "threshold-20", "seed": "42", "playerCount": 3,
                    "assignments": [
                      { "seatIndex": 0, "faction": "ERASERS", "policyId": "random", "policyVersion": "v1" },
                      { "seatIndex": 1, "faction": "ACTIVISTS", "policyId": "random", "policyVersion": "v1" },
                      { "seatIndex": 2, "faction": "WEAVERS", "policyId": "random", "policyVersion": "v1" }
                    ]
                  } ],
                  "limit": 100,
                  "offset": 0
                }
                """.formatted(DIGEST, ID);

        assertValid(validator.validateResponse("/api/v1/experiment-previews", Request.Method.POST, json(preview)));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/experiment-previews",
                        Request.Method.POST,
                        json(preview.replace("\"caseCount\": 55,", "")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aCaseSummaryAlwaysNamesItsEndReasonEvenWhenThereIsNone() {
        var summary = """
                { "items": [ {
                    "caseId": "%s", "caseKey": "%s", "runId": "%s", "variantLabel": "threshold-20",
                    "seed": "42", "playerCount": 3, "state": "FAILED", "endReason": null
                  } ], "total": 1 }
                """.formatted(ID, ID, ID);

        assertValid(validator.validateResponse("/api/v1/runs/" + ID + "/cases", Request.Method.GET, json(summary)));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/runs/" + ID + "/cases",
                        Request.Method.GET,
                        json(summary.replace("\"endReason\": null", "\"endReason\": \"BOGUS\"")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void anInvalidExperimentNamesTheOffendingManifestValue() {
        var problem = """
                { "type": "about:blank", "title": "Bad Request", "status": 400,
                  "detail": "seeds must be unique: 5", "code": "INVALID_EXPERIMENT", "field": "seeds" }
                """;

        assertValid(validator.validateResponse(
                "/api/v1/experiment-previews", Request.Method.POST, problemJson(400, problem)));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/experiment-previews",
                        Request.Method.POST,
                        problemJson(400, problem.replace("\"field\": \"seeds\"", "\"field\": 3")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aNullableEnumListsNullSoStrictClientsAcceptIt() {
        var schemas = openApi.getComponents().getSchemas();

        assertTrue(enumOf(schemas.get("CaseSummary"), "endReason").contains(null));
        assertTrue(enumOf(schemas.get("Winner"), "winType").contains(null));
    }

    private static List<?> enumOf(Schema<?> schema, String property) {
        return ((Schema<?>) schema.getProperties().get(property)).getEnum();
    }

    @Test
    void listsReturnAnItemsAndTotalEnvelope() {
        var experiments = """
                { "items": [ { "experimentId": "%s", "name": "e", "manifestDigest": "%s",
                               "createdAt": "2026-10-09T12:00:00Z" } ], "total": 3 }
                """.formatted(ID, DIGEST);

        assertValid(validator.validateResponse("/api/v1/experiments", Request.Method.GET, json(experiments)));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/experiments", Request.Method.GET, json(experiments.replace(", \"total\": 3", "")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aComparisonListNamesEachComparisonWithoutComputingIt() {
        var list = """
                { "items": [ {
                    "comparisonId": "%s",
                    "baseline": { "runId": "%s", "variantLabel": "threshold-20" },
                    "candidate": { "runId": "%s", "variantLabel": "threshold-22" },
                    "analysisVersion": "1", "analysisSeed": "42", "createdAt": "2026-10-09T12:00:00Z"
                  } ], "total": 1 }
                """.formatted(ID, ID, ID);

        assertValid(validator.validateResponse("/api/v1/comparisons", Request.Method.GET, json(list)));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/comparisons",
                        Request.Method.GET,
                        json(list.replace("\"createdAt\": \"2026-10-09T12:00:00Z\"", "\"cohorts\": []")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void thePolicyCatalogNamesEachBundleByIdVersionAndDigest() {
        var catalog = """
                { "items": [ { "id": "random", "version": "v1", "artifactDigest": "%s",
                               "description": "Uniform random choice", "parameters": {} } ] }
                """.formatted(DIGEST);

        assertValid(validator.validateResponse("/api/v1/policies", Request.Method.GET, json(catalog)));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/policies", Request.Method.GET, json(catalog.replace("\"artifactDigest\"", "\"digest\"")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void pagingIsBoundedAndDefaultedConsistently() {
        var limit = openApi.getComponents().getParameters().get("Limit").getSchema();
        var offset = openApi.getComponents().getParameters().get("Offset").getSchema();

        assertEquals(500, limit.getMaximum().intValue());
        assertEquals(50, ((Number) limit.getDefault()).intValue());
        assertEquals(0, offset.getMinimum().intValue());
        assertTrue(openApi.getPaths().get("/api/v1/runs").getGet().getParameters().size() >= 4);
        assertTrue(openApi.getPaths().get("/api/v1/lane-bundles").getGet().getParameters().size() >= 2);
        assertTrue(openApi.getPaths().get("/api/v1/experiment-templates").getGet().getParameters().size() >= 2);
    }

    @Test
    void laneBundlesCarryProvenanceContractsAndLaneCountInDigestOrder() {
        var bundles = """
                { "items": [
                    { "rulesArtifact": { "digest": "%1$s" }, "contentArtifact": { "digest": "%1$s" },
                      "effectiveRulesDigest": "%1$s", "effectiveContentDigest": "%1$s",
                      "services": {
                        "gameService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" },
                        "timelineService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" },
                        "readService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" } },
                      "contracts": { "simulation-api": "3.2.0" }, "laneCount": 3 },
                    { "rulesArtifact": { "digest": "%2$s" }, "contentArtifact": { "digest": "%2$s" },
                      "effectiveRulesDigest": "%2$s", "effectiveContentDigest": "%1$s",
                      "services": {
                        "gameService": { "imageDigest": "sha256:%2$s", "sourceRevision": "def5678" },
                        "timelineService": { "imageDigest": "sha256:%2$s", "sourceRevision": "def5678" },
                        "readService": { "imageDigest": "sha256:%2$s", "sourceRevision": "def5678" } },
                      "contracts": { "simulation-api": "3.2.0" }, "laneCount": 1 }
                  ], "total": 2 }
                """.formatted(DIGEST, "b".repeat(64));

        assertValid(validator.validateResponse("/api/v1/lane-bundles", Request.Method.GET, json(bundles)));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/lane-bundles",
                        Request.Method.GET,
                        json(bundles.replace("\"laneCount\": 3", "")))
                .getMessages()
                .isEmpty());

        var description = openApi.getPaths().get("/api/v1/lane-bundles").getGet().getDescription();
        assertTrue(description.contains("ascending `effectiveRulesDigest`"));
        assertTrue(description.contains("ascending\n        `effectiveContentDigest`")
                || description.contains("`effectiveContentDigest`"));
        assertTrue(description.contains("never")
                || description.contains("Never returns a lane identifier"));
    }

    @Test
    void anEmptyLaneBundlePageIsValid() {
        var empty = """
                { "items": [], "total": 0 }
                """;

        assertValid(validator.validateResponse("/api/v1/lane-bundles", Request.Method.GET, json(empty)));
    }

    @Test
    void experimentTemplatesCarryCompleteDefaultsInTemplateOrder() {
        var templates = """
                { "items": [ {
                    "templateId": "threshold-sweep", "name": "Threshold sweep",
                    "description": "Vary the score threshold across two bundles",
                    "variantCount": 2,
                    "defaults": {
                      "seeds": ["42"],
                      "playerCounts": [3],
                      "factionSets": [["ERASERS", "ACTIVISTS", "WEAVERS"]],
                      "seatRotationMode": "CYCLIC", "timingMode": "LOGICAL",
                      "policies": [ { "id": "random", "version": "v1", "artifactDigest": "%s",
                                     "parameters": {},
                                     "seatAssignment": [
                                       { "seatIndex": 0, "policyId": "random", "policyVersion": "v1" } ] } ],
                      "concurrency": 1, "caseWallTimeoutSeconds": 60,
                      "maxRejectedCandidatesPerWindow": 10
                    }
                  } ], "total": 1 }
                """.formatted(DIGEST);

        assertValid(validator.validateResponse("/api/v1/experiment-templates", Request.Method.GET, json(templates)));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/experiment-templates",
                        Request.Method.GET,
                        json(templates.replace("\"variantCount\": 2,", "")))
                .getMessages()
                .isEmpty());

        var description =
                openApi.getPaths().get("/api/v1/experiment-templates").getGet().getDescription();
        assertTrue(description.contains("ascending `templateId`"));
    }

    @Test
    void anOutOfRangePageIsAnInvalidRequest() {
        var problem = """
                { "type": "about:blank", "title": "Bad Request", "status": 400,
                  "detail": "limit must be between 1 and 500", "code": "INVALID_REQUEST" }
                """;

        assertValid(validator.validateResponse(
                "/api/v1/lane-bundles", Request.Method.GET, problemJson(400, problem)));
        assertValid(validator.validateResponse(
                "/api/v1/experiment-templates", Request.Method.GET, problemJson(400, problem)));
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
