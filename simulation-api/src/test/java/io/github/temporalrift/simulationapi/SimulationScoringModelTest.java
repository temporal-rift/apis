package io.github.temporalrift.simulationapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import io.swagger.v3.parser.OpenAPIV3Parser;

/** Pins the scoring-model evaluation and run re-scoring operations and their shared scoring engine. */
class SimulationScoringModelTest {

    private static final String SPECIFICATION = "openapi/v1/simulation.yml";
    private static final String RUN_ID = "6f3f2a52-4f0a-4c69-9a39-2a8f0e2b9d11";
    private static final String ENGINE = """
            { "winThreshold": 24, "maxEras": 3,
              "factions": [
                { "faction": "WEAVERS", "winPoints": [2, 4, 6], "missPoints": -1, "resetOn": ["MISSED"],
                  "objectiveWins": 3, "objectiveBonus": 5 },
                { "faction": "ACTIVISTS", "winPoints": [3, 5, 7], "missPoints": 0, "resetOn": [] } ] }
            """;
    private final String url = Objects.requireNonNull(getClass().getClassLoader().getResource(SPECIFICATION))
            .toString();
    private final OpenAPI openApi = new OpenAPIV3Parser().read(url);
    private final OpenApiInteractionValidator validator =
            OpenApiInteractionValidator.createForSpecificationUrl(url).build();

    @Test
    void bothOperationsRequireTheReadScope() {
        var evaluate = openApi.getPaths().get("/api/v1/scoring-models/evaluations").getPost();
        var rescore = openApi.getPaths().get("/api/v1/runs/{runId}/rescorings").getPost();

        assertEquals("evaluateScoringModel", evaluate.getOperationId());
        assertEquals("rescoreRun", rescore.getOperationId());
        assertEquals(List.of("simulation:read"), evaluate.getExtensions().get("x-required-scopes"));
        assertEquals(List.of("simulation:read"), rescore.getExtensions().get("x-required-scopes"));
    }

    @Test
    void definesTheScoringSchemasAndTheBetOutcome() {
        var schemas = openApi.getComponents().getSchemas();

        List.of(
                        "ScoringEngine",
                        "FactionScoringEngine",
                        "ScoringModelRequest",
                        "BetRates",
                        "SweepParameter",
                        "ScoringModelEvaluation",
                        "RescoreRequest",
                        "ReasonPoints",
                        "Rescoring")
                .forEach(name -> assertNotNull(schemas.get(name), name));
        assertEquals(
                List.of("WON", "MISSED", "VOIDED", "NOT_PLACED"),
                schemas.get("BetOutcome").getEnum());
    }

    @Test
    void specVersionIs8_2_0() {
        assertEquals("8.2.0", openApi.getInfo().getVersion());
    }

    @Test
    void anEvaluationRequestAndResponseValidate() {
        assertValid(validator.validateRequest(evaluate(request(ENGINE, "0.4", "0.3"))));
        assertValid(validator.validateResponse("/api/v1/scoring-models/evaluations", Request.Method.POST, json("""
                { "cells": [ { "playerCount": 3, "faction": "WEAVERS", "ratio": 1.04, "lower": 1.0, "upper": 1.08 } ],
                  "endings": [ { "playerCount": 3, "meanEras": 2.4, "thresholdShare": 0.7, "finalEraShare": 0.3 } ],
                  "sensitivity": [ { "parameter": "factions.WEAVERS.winPoints[2]", "delta": 1,
                                     "playerCount": 3, "faction": "WEAVERS", "ratioChange": 0.3 } ] }
                """)));
    }

    @Test
    void anEmptyWinPointsListIsRejected() {
        var engine = ENGINE.replace("[2, 4, 6]", "[]");

        assertFalse(validator
                .validateRequest(evaluate(request(engine, "0.4", "0.3")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void theWinPointsListMayNotExceedTenEntries() {
        var engine = ENGINE.replace("[2, 4, 6]", "[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1]");

        assertFalse(validator
                .validateRequest(evaluate(request(engine, "0.4", "0.3")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aWonOutcomeCannotResetTheWinRun() {
        var engine = ENGINE.replace("\"resetOn\": [\"MISSED\"]", "\"resetOn\": [\"WON\"]");

        assertFalse(validator
                .validateRequest(evaluate(request(engine, "0.4", "0.3")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aSweepStepOfZeroOrAnUnknownPathIsRejected() {
        assertFalse(validator
                .validateRequest(evaluate(request(ENGINE, "0.4", "0.3", sweep("winThreshold", "0"))))
                .getMessages()
                .isEmpty());
        assertFalse(validator
                .validateRequest(evaluate(request(ENGINE, "0.4", "0.3", sweep("factions.WEAVERS.winPoints", "1"))))
                .getMessages()
                .isEmpty());
    }

    @Test
    void everyAcceptedSweepPathFormValidates() {
        List.of(
                        "winThreshold",
                        "factions.WEAVERS.winPoints[2]",
                        "factions.WEAVERS.missPoints",
                        "factions.WEAVERS.objectiveBonus",
                        "rates.3.WEAVERS.winRate")
                .forEach(path -> assertValid(
                        validator.validateRequest(evaluate(request(ENGINE, "0.4", "0.3", sweep(path, "-1.5"))))));
    }

    @Test
    void theOperationDescriptionStatesTheRatesRefusal() {
        var description = openApi.getPaths()
                .get("/api/v1/scoring-models/evaluations")
                .getPost()
                .getDescription();

        assertTrue(description.contains("400 INVALID_REQUEST"));
        assertTrue(description.contains("`rates[3]`"));
        assertTrue(description.contains("`winRate + missRate`"));
    }

    @Test
    void aRescoringRequestAndResponseValidate() {
        assertValid(validator.validateRequest(rescore("""
                { "engine": %s, "variantLabel": "baseline", "tableId": "mixed" }
                """.formatted(ENGINE))));
        assertValid(validator.validateResponse("/api/v1/runs/{runId}/rescorings", Request.Method.POST, json("""
                { "complete": false,
                  "cells": [ { "playerCount": 4, "faction": "ACTIVISTS", "ratio": 0.97, "lower": 0.9, "upper": 1.04 } ],
                  "playerCounts": [ { "playerCount": 4, "judgedCases": 90, "truncatedCases": 10, "meanEras": 2.2 } ] }
                """)));
    }

    @Test
    void anAllTruncatedRescoringHasNoCellsAndANullMeanEras() {
        assertValid(validator.validateResponse("/api/v1/runs/{runId}/rescorings", Request.Method.POST, json("""
                { "complete": true, "cells": [],
                  "playerCounts": [ { "playerCount": 3, "judgedCases": 0, "truncatedCases": 12, "meanEras": null } ] }
                """)));
    }

    @Test
    void aRescoringWithOtherReasonPointsValidates() {
        assertValid(validator.validateRequest(rescore("""
                { "engine": %s, "variantLabel": "baseline", "tableId": "mixed",
                  "otherReasonPoints": [
                    { "scoreReason": "CHAIN_COMPLETED", "points": 0 },
                    { "scoreReason": "CORRUPTED_OPPONENT_CARD", "points": 2 } ] }
                """.formatted(ENGINE))));
    }

    @Test
    void anEmptyOtherReasonPointsValidates() {
        assertValid(validator.validateRequest(rescore("""
                { "engine": %s, "variantLabel": "baseline", "tableId": "mixed",
                  "otherReasonPoints": [] }
                """.formatted(ENGINE))));
    }

    @Test
    void otherReasonPointsBeyondFiftyAreRejected() {
        assertFalse(validator
                .validateRequest(rescore("""
                        { "engine": %s, "variantLabel": "baseline", "tableId": "mixed",
                          "otherReasonPoints": [ { "scoreReason": "CHAIN_COMPLETED", "points": 51 } ] }
                        """.formatted(ENGINE)))
                .getMessages()
                .isEmpty());
    }

    @Test
    void rescoringStatesTheOtherReasonRules() {
        var description = openApi.getPaths()
                .get("/api/v1/runs/{runId}/rescorings")
                .getPost()
                .getDescription();

        assertTrue(description.contains("unless named in `otherReasonPoints`"));
        assertTrue(description.contains("Reasons not named"));
        assertTrue(description.contains("keep their recorded value"));
        assertTrue(description.contains("`ANNIHILATED_OUTCOME`"));
        assertTrue(description.contains("not configured by the run's variant"));
        assertTrue(description.contains("appears twice"));
    }

    @Test
    void rescoringStatesTheEmptyAndMissingFactionRules() {
        var description = openApi.getPaths()
                .get("/api/v1/runs/{runId}/rescorings")
                .getPost()
                .getDescription();

        assertTrue(description.contains("null `meanEras`"));
        assertTrue(description.contains("`field` `engine.factions`"));
        assertTrue(description.contains("while the run's recorded cases are unchanged"));
    }

    @Test
    void rescoringDocumentsAnUnknownRunAsNotFound() {
        assertValid(validator.validateResponse(
                "/api/v1/runs/{runId}/rescorings", Request.Method.POST, problemJson(404, """
                        { "type": "https://temporal-rift.example/problems/resource-not-found",
                          "title": "Not found", "status": 404, "code": "RESOURCE_NOT_FOUND" }
                        """)));
        assertTrue(openApi.getPaths()
                .get("/api/v1/runs/{runId}/rescorings")
                .getPost()
                .getResponses()
                .containsKey("404"));
    }

    private static String request(String engine, String winRate, String missRate) {
        return request(engine, winRate, missRate, "");
    }

    private static String request(String engine, String winRate, String missRate, String sweep) {
        return """
                { "engine": %s,
                  "playerCounts": [3],
                  "rates": [
                    { "playerCount": 3, "faction": "WEAVERS", "winRate": %s, "missRate": %s, "extraPointsPerEra": 1.5 },
                    { "playerCount": 3, "faction": "ACTIVISTS", "winRate": %s, "missRate": %s, "extraPointsPerEra": 0 } ],
                  "seed": 42%s }
                """.formatted(engine, winRate, missRate, winRate, missRate, sweep);
    }

    private static String sweep(String parameter, String step) {
        return ", \"sweep\": [ { \"parameter\": \"%s\", \"step\": %s } ]".formatted(parameter, step);
    }

    private static Request evaluate(String body) {
        return SimpleRequest.Builder.post("/api/v1/scoring-models/evaluations")
                .withHeader("Authorization", "Bearer token")
                .withContentType("application/json")
                .withBody(body)
                .build();
    }

    private static Request rescore(String body) {
        return SimpleRequest.Builder.post("/api/v1/runs/" + RUN_ID + "/rescorings")
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
