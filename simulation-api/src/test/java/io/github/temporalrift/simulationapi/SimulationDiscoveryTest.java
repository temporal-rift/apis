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
                "/api/v1/policies", List.of("simulation:read"));

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
    }

    private static SimpleResponse json(String body) {
        return SimpleResponse.Builder.ok()
                .withContentType("application/json")
                .withBody(body)
                .build();
    }

    private static void assertValid(ValidationReport report) {
        assertTrue(report.getMessages().isEmpty(), report::toString);
    }
}
