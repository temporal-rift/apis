package io.github.temporalrift.simulationapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;

/** Covers the balance criterion catalog, run evaluation, and failure_share report metric. */
class SimulationBalanceEvaluationTest {

    private static final String SPECIFICATION = "openapi/v1/simulation.yml";
    private static final String RUN_ID = "6f3f2a52-4f0a-4c69-9a39-2a8f0e2b9d11";
    private static final String DIGEST = "a".repeat(64);

    private final String url = Objects.requireNonNull(getClass().getClassLoader().getResource(SPECIFICATION))
            .toString();
    private final OpenAPI openApi = new OpenAPIV3Parser().read(url);
    private final OpenApiInteractionValidator validator =
            OpenApiInteractionValidator.createForSpecificationUrl(url).build();

    @Test
    void criterionVersionOneEncodesEveryApprovedLine() throws IOException {
        var criterion = fixture("balance-criterion-v1.json");

        assertValid(validator.validateResponse("/api/v1/balance-criteria/1", Request.Method.GET, json(criterion)));
        List.of("G-01", "G-02", "G-03", "G-04", "G-05", "G-06", "G-07", "G-08",
                        "L-01", "L-02", "L-03", "L-04", "L-05", "L-06", "L-07")
                .forEach(id -> assertTrue(criterion.contains("\"id\": \"" + id + "\""), id));
        assertTrue(criterion.contains("\"aggregation\": \"WEIGHTED_SUM_OF_SQUARED_DEVIATIONS\""));
        assertTrue(criterion.contains("\"confirmationMinimumSeeds\": 100"));
    }

    @Test
    void aLineIdOutsideTheVocabularyIsRejected() throws IOException {
        var criterion = fixture("balance-criterion-v1.json").replace("\"id\": \"G-01\",", "\"id\": \"X-01\",");

        assertFalse(validator
                .validateResponse("/api/v1/balance-criteria/1", Request.Method.GET, json(criterion))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aScoredLineOutsideItsBandFailsAndContributesWeightTimesDeviationSquared() {
        var evaluation = evaluation(
                """
                        "verdict": "UNCONFIRMED",
                        "score": 0.02962962962962963,
                        "confirmationSample": null,
                        "lines": [ {
                          "lineId": "L-01", "role": "SCORED", "metric": "faction_win_rate",
                          "dimensions": { "faction": "ERASERS" },
                          "policyId": "faction-greedy-v1", "playerCount": 3, "factionSet": null,
                          "band": { "lower": 0.8, "upper": 1.25, "scale": null },
                          "value": 1.40,
                          "interval": { "level": 0.95, "lower": 1.30, "upper": 1.50, "method": "BLOCK_BOOTSTRAP" },
                          "status": "AVAILABLE", "deviation": 0.3333333333333333, "verdict": "FAILS",
                          "weight": 0.26666666666666666
                        } ]
                        """,
                "SCREENING",
                """
                        "valid": true, "reasons": []
                        """);

        assertValid(validator.validateResponse(
                "/api/v1/runs/" + RUN_ID + "/evaluation", Request.Method.GET, json(evaluation)));
        // (1.40 - 1.25) / (1.25 - 0.8) = 1/3; contribution: 4/15 x (1/3)^2 = 4/135.
        assertEquals(4.0 / 135.0, 0.02962962962962963, 1e-12);
    }

    @Test
    void aOneSidedGateAtItsLimitMeetsWithZeroDeviation() {
        var evaluation = evaluation(
                """
                        "verdict": "UNCONFIRMED",
                        "score": 0,
                        "confirmationSample": null,
                        "lines": [ {
                          "lineId": "G-02", "role": "GATE", "metric": "ending_cause_rate",
                          "dimensions": { "endReason": "RESOLUTION_FAILED" },
                          "policyId": "faction-greedy-v1", "playerCount": 3, "factionSet": null,
                          "band": { "lower": null, "upper": 0, "scale": 0.01 },
                          "value": 0,
                          "interval": { "level": 0.95, "lower": 0, "upper": 0, "method": "BLOCK_BOOTSTRAP" },
                          "status": "AVAILABLE", "deviation": 0, "verdict": "MEETS",
                          "weight": null
                        } ]
                        """,
                "SCREENING",
                """
                        "valid": true, "reasons": []
                        """);

        assertValid(validator.validateResponse(
                "/api/v1/runs/" + RUN_ID + "/evaluation", Request.Method.GET, json(evaluation)));
    }

    @Test
    void anInsufficientSampleLineIsInconclusiveWithoutAnInterval() {
        var evaluation = evaluation(
                """
                        "verdict": "UNCONFIRMED",
                        "score": 0,
                        "confirmationSample": null,
                        "lines": [ {
                          "lineId": "L-02", "role": "SCORED", "metric": "eras",
                          "dimensions": { },
                          "policyId": "faction-greedy-v1", "playerCount": 5, "factionSet": null,
                          "band": { "lower": 3.5, "upper": 4.5, "scale": null },
                          "value": 3.9, "interval": null,
                          "status": "INSUFFICIENT_SAMPLE", "deviation": 0, "verdict": "INCONCLUSIVE",
                          "weight": 0.6666666666666666
                        } ]
                        """,
                "SCREENING",
                """
                        "valid": true, "reasons": []
                        """);

        assertValid(validator.validateResponse(
                "/api/v1/runs/" + RUN_ID + "/evaluation", Request.Method.GET, json(evaluation)));
    }

    @Test
    void configurationDriftMakesTheEvaluationInvalidWithANullScore() {
        var evaluation = evaluation(
                """
                        "verdict": "INVALID",
                        "score": null,
                        "confirmationSample": null,
                        "lines": [ ]
                        """,
                "SCREENING",
                """
                        "valid": false,
                        "reasons": [ { "code": "CONFIGURATION_DRIFT", "detail": "case %s drifted" } ]
                        """.formatted(RUN_ID));

        assertValid(validator.validateResponse(
                "/api/v1/runs/" + RUN_ID + "/evaluation", Request.Method.GET, json(evaluation)));
    }

    @Test
    void screeningNeverConfirmsEvenWhenEveryLineMeets() {
        var evaluation = evaluation(
                """
                        "verdict": "UNCONFIRMED",
                        "score": 0,
                        "confirmationSample": null,
                        "lines": [ ]
                        """,
                "SCREENING",
                """
                        "valid": true, "reasons": []
                        """);

        assertValid(validator.validateResponse(
                "/api/v1/runs/" + RUN_ID + "/evaluation", Request.Method.GET, json(evaluation)));
    }

    @Test
    void aShortConfirmationSampleDoesNotConfirm() {
        var evaluation = evaluation(
                """
                        "verdict": "UNCONFIRMED",
                        "score": 0,
                        "confirmationSample": {
                          "requiredSeeds": 100, "seeds": 60, "fullMatrix": true, "satisfied": false
                        },
                        "lines": [ ]
                        """,
                "CONFIRMATION",
                """
                        "valid": true, "reasons": []
                        """);

        assertValid(validator.validateResponse(
                "/api/v1/runs/" + RUN_ID + "/evaluation", Request.Method.GET, json(evaluation)));
    }

    @Test
    void failureShareAppearsPooledWithAndWithoutACode() {
        var report = """
                {
                  "formatVersion": 2,
                  "runId": "%s",
                  "manifestDigest": "%s",
                  "analysisVersion": "2",
                  "analysisSeed": "1",
                  "complete": true,
                  "caseCounts": { "requested": 10, "pending": 0, "running": 0, "succeeded": 8, "failed": 2, "cancelled": 0 },
                  "cohorts": [ {
                    "variantLabel": "threshold-20",
                    "policyId": "faction-greedy-v1",
                    "policyVersion": "1.0.0",
                    "playerCount": 3,
                    "factionSet": null,
                    "seatIndex": null,
                    "caseCounts": { "requested": 10, "pending": 0, "running": 0, "succeeded": 8, "failed": 2, "cancelled": 0 },
                    "eligibleGames": 8,
                    "independentBlocks": 5,
                    "metrics": [
                      {
                        "name": "failure_share",
                        "statistic": "RATE",
                        "dimensions": { },
                        "value": 0.2,
                        "numerator": 2,
                        "denominator": 10,
                        "unit": "cases",
                        "status": "AVAILABLE",
                        "interval": { "level": 0.95, "lower": 0, "upper": 0.4, "method": "BLOCK_BOOTSTRAP" },
                        "unknownCount": 0
                      },
                      {
                        "name": "failure_share",
                        "statistic": "RATE",
                        "dimensions": { "failureCode": "RUNNER_TIMEOUT" },
                        "value": 0.2,
                        "numerator": 2,
                        "denominator": 10,
                        "unit": "cases",
                        "status": "AVAILABLE",
                        "interval": { "level": 0.95, "lower": 0, "upper": 0.4, "method": "BLOCK_BOOTSTRAP" },
                        "unknownCount": 0
                      }
                    ]
                  } ]
                }
                """.formatted(RUN_ID, DIGEST);

        assertValid(validator.validateResponse("/api/v1/runs/" + RUN_ID + "/report", Request.Method.GET, json(report)));
    }

    @Test
    void aFailureCodeOutsideTheVocabularyIsRejected() {
        var report = """
                {
                  "formatVersion": 2,
                  "runId": "%s",
                  "manifestDigest": "%s",
                  "analysisVersion": "2",
                  "analysisSeed": "1",
                  "complete": true,
                  "caseCounts": { "requested": 10, "pending": 0, "running": 0, "succeeded": 8, "failed": 2, "cancelled": 0 },
                  "cohorts": [ {
                    "variantLabel": "threshold-20",
                    "policyId": "faction-greedy-v1",
                    "policyVersion": "1.0.0",
                    "playerCount": 3,
                    "factionSet": null,
                    "seatIndex": null,
                    "caseCounts": { "requested": 10, "pending": 0, "running": 0, "succeeded": 8, "failed": 2, "cancelled": 0 },
                    "eligibleGames": 8,
                    "independentBlocks": 5,
                    "metrics": [
                      {
                        "name": "failure_share",
                        "statistic": "RATE",
                        "dimensions": { "failureCode": "BOGUS" },
                        "value": 0.2,
                        "numerator": 2,
                        "denominator": 10,
                        "unit": "cases",
                        "status": "AVAILABLE",
                        "interval": { "level": 0.95, "lower": 0, "upper": 0.4, "method": "BLOCK_BOOTSTRAP" },
                        "unknownCount": 0
                      }
                    ]
                  } ]
                }
                """.formatted(RUN_ID, DIGEST);

        assertFalse(validator
                .validateResponse("/api/v1/runs/" + RUN_ID + "/report", Request.Method.GET, json(report))
                .getMessages()
                .isEmpty());
    }

    @Test
    void evaluationReadsAreScopedAndTheirErrorsAreMapped() {
        var evaluation = openApi.getPaths().get("/api/v1/runs/{runId}/evaluation").getGet();
        assertEquals(List.of("simulation:read"), evaluation.getExtensions().get("x-required-scopes"));
        assertTrue(evaluation.getResponses().containsKey("400"));
        assertTrue(evaluation.getResponses().containsKey("404"));

        var parameters = evaluation.getParameters();
        var variantLabel = parameter(parameters, "variantLabel");
        var criterionVersion = parameter(parameters, "criterionVersion");
        var mode = parameter(parameters, "mode");
        assertTrue(variantLabel.getRequired());
        assertTrue(criterionVersion.getRequired());
        assertFalse(mode.getRequired());
        assertEquals(List.of("SCREENING", "CONFIRMATION"), mode.getSchema().getEnum());
        assertEquals("SCREENING", mode.getSchema().getDefault());

        var catalog = openApi.getPaths().get("/api/v1/balance-criteria").getGet();
        assertEquals(List.of("simulation:read"), catalog.getExtensions().get("x-required-scopes"));
        var single = openApi.getPaths().get("/api/v1/balance-criteria/{criterionVersion}").getGet();
        assertEquals(List.of("simulation:read"), single.getExtensions().get("x-required-scopes"));
        assertTrue(single.getResponses().containsKey("404"));
    }

    private static Parameter parameter(List<Parameter> parameters, String name) {
        return parameters.stream()
                .filter(parameter -> parameter.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing parameter " + name));
    }

    private static String evaluation(String tail, String mode, String validity) {
        return """
                {
                  "runId": "%s",
                  "variantLabel": "threshold-20",
                  "criterionVersion": 1,
                  "criterionDigest": "%s",
                  "analysisVersion": "2",
                  "analysisSeed": "1",
                  "mode": "%s",
                  "validity": { %s },
                  %s
                }
                """.formatted(RUN_ID, DIGEST, mode, validity, tail);
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

    private String fixture(String name) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/" + name)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
