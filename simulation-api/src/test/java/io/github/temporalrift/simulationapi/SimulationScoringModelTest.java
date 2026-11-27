package io.github.temporalrift.simulationapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            { "maxEras": 5,
              "reasonPoints": [
                { "scoreReason": "ANNIHILATED_OUTCOME", "points": 6 },
                { "scoreReason": "ANNIHILATION_WASTED", "points": -1 },
                { "scoreReason": "EVENT_RESOLVED_AS_WRITTEN", "points": 7 },
                { "scoreReason": "FULFILLMENT_SUCCEEDED", "points": 14 },
                { "scoreReason": "EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN", "points": -4 },
                { "scoreReason": "SECRET_OUTCOME_WON", "points": 6 },
                { "scoreReason": "SECRET_OUTCOME_MISSED", "points": -2 },
                { "scoreReason": "CHAIN_LINK_MISSED", "points": 0 },
                { "scoreReason": "DECLARED_OUTCOME_MISSED", "points": -2 },
                { "scoreReason": "DECLARED_OUTCOME_MISSED_WITH_RALLY", "points": -4 },
                { "scoreReason": "CHAIN_COMPLETED", "points": 0 },
                { "scoreReason": "CHAIN_BROKEN", "points": 0 },
                { "scoreReason": "CORRUPTED_OPPONENT_CARD", "points": 2 } ],
              "escalations": [
                { "name": "weaver-link", "scoreReasons": ["CHAIN_LINK_ADDED"],
                  "base": 6, "increment": 3, "resetOn": ["VOIDED"] },
                { "name": "activist-streak",
                  "scoreReasons": ["DECLARED_OUTCOME_WON", "DECLARED_OUTCOME_WON_WITH_RALLY"],
                  "base": 6, "increment": 2, "resetOn": ["MISSED", "VOIDED", "NOT_PLACED"] } ] }
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
                        "Escalation",
                        "ReasonPoints",
                        "SeatEraRates",
                        "ScoringModelRequest",
                        "SweepParameter",
                        "ScoringModelEvaluation",
                        "RescoreRequest",
                        "Rescoring",
                        "RescoringSensitivity",
                        "RescoringSensitivityCell")
                .forEach(name -> assertNotNull(schemas.get(name), name));
        assertNull(schemas.get("FactionScoringEngine"));
        assertNull(schemas.get("BetRates"));
        assertNull(schemas.get("ScoringEnding"));
        assertEquals(
                List.of("WON", "MISSED", "VOIDED", "NOT_PLACED"),
                schemas.get("BetOutcome").getEnum());
    }

    @Test
    void specVersionIs9_1_0() {
        assertEquals("9.1.0", openApi.getInfo().getVersion());
    }

    @Test
    void anEvaluationRequestAndResponseValidate() {
        assertValid(validator.validateRequest(evaluate(request())));
        assertValid(validator.validateResponse("/api/v1/scoring-models/evaluations", Request.Method.POST, json("""
                { "cells": [ { "playerCount": 3, "faction": "WEAVERS", "ratio": 1.04, "lower": 1.0, "upper": 1.08 } ],
                  "sensitivity": [ { "parameter": "reasonPoints.ANNIHILATED_OUTCOME", "delta": 1,
                                     "playerCount": 3, "faction": "WEAVERS", "ratioChange": 0.3 } ] }
                """)));
    }

    @Test
    void theApprovedDesignAsAnEngineValidates() {
        assertValid(validator.validateRequest(evaluate(request())));
    }

    @Test
    void anIncrementBeyondTwentyIsRejected() {
        var engine = ENGINE.replace("\"increment\": 3", "\"increment\": 21");

        assertFalse(validator
                .validateRequest(evaluate(request(engine)))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aBaseBeyondFiftyIsRejected() {
        var engine = ENGINE.replace("\"base\": 6, \"increment\": 3", "\"base\": 51, \"increment\": 3");

        assertFalse(validator
                .validateRequest(evaluate(request(engine)))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aWonOutcomeCannotResetTheRun() {
        var engine = ENGINE.replace("\"resetOn\": [\"VOIDED\"]", "\"resetOn\": [\"WON\"]");

        assertFalse(validator
                .validateRequest(evaluate(request(engine)))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aSeatEraRatesItemWithoutVoidedRateIsRejected() {
        var body = """
                { "engine": %s, "factions": ["WEAVERS"],
                  "playerCounts": [3],
                  "rates": [ { "playerCount": 3, "faction": "WEAVERS",
                    "betReasons": [ { "scoreReason": "CHAIN_LINK_ADDED", "rate": 0.4 } ] } ],
                  "seed": 42 }
                """.formatted(ENGINE);

        assertFalse(validator.validateRequest(evaluate(body)).getMessages().isEmpty());
    }

    @Test
    void aSweepStepOfZeroOrAnUnknownPathIsRejected() {
        assertFalse(validator
                .validateRequest(evaluate(requestWithSweep(sweep("maxEras", "0"))))
                .getMessages()
                .isEmpty());
        assertFalse(validator
                .validateRequest(evaluate(requestWithSweep(sweep("factions.WEAVERS.winPoints", "1"))))
                .getMessages()
                .isEmpty());
    }

    @Test
    void everyAcceptedSweepPathFormValidates() {
        List.of(
                        "maxEras",
                        "reasonPoints.ANNIHILATED_OUTCOME",
                        "escalations.weaver-link.base",
                        "escalations.weaver-link.increment",
                        "rates.3.WEAVERS.betReasons.CHAIN_LINK_ADDED",
                        "rates.3.WEAVERS.voidedRate",
                        "rates.3.WEAVERS.otherReasons.CORRUPTED_OPPONENT_CARD")
                .forEach(path -> assertValid(
                        validator.validateRequest(evaluate(requestWithSweep(sweep(path, "1"))))));
    }

    @Test
    void aReasonNameWithDotsCanBeSwept() {
        var dottedEngine = """
                { "maxEras": 5,
                  "reasonPoints": [ { "scoreReason": "A.B", "points": 1 } ],
                  "escalations": [] }
                """;

        assertValid(validator.validateRequest(
                evaluate(request(dottedEngine, sweep("reasonPoints.A.B", "1")))));
    }

    @Test
    void theOperationDescriptionStatesTheRatesRefusal() {
        var description = openApi.getPaths()
                .get("/api/v1/scoring-models/evaluations")
                .getPost()
                .getDescription();

        assertTrue(description.contains("400 INVALID_REQUEST"));
        assertTrue(description.contains("`field` `rates`"));
        assertTrue(description.contains("at most one bet reason per seat-era"));
    }

    @Test
    void theOperationDescriptionStatesScoringOrderAndBetTable() {
        var description = openApi.getPaths()
                .get("/api/v1/scoring-models/evaluations")
                .getPost()
                .getDescription();

        assertTrue(description.contains("every occurrence of a score reason"));
        assertTrue(description.contains("resets its run to 0"));
        assertTrue(description.contains("ANNIHILATED_OUTCOME"));
        assertTrue(description.contains("DECLARED_OUTCOME_WON_WITH_RALLY"));
        assertTrue(description.contains("no win threshold and no objectives"));
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
    void aRescoringWithOtherReasonPointsIsRejected() {
        assertFalse(validator
                .validateRequest(rescore("""
                        { "engine": %s, "variantLabel": "baseline", "tableId": "mixed",
                          "otherReasonPoints": [ { "scoreReason": "CHAIN_COMPLETED", "points": 0 } ] }
                        """.formatted(ENGINE)))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aRescoringSweepRequestValidates() {
        assertValid(validator.validateRequest(rescore("""
                { "engine": %s, "variantLabel": "baseline", "tableId": "mixed",
                  "sweep": ["reasonPoints.CORRUPTED_OPPONENT_CARD",
                    "escalations.weaver-link.base", "escalations.activist-streak.increment"] }
                """.formatted(ENGINE))));
    }

    @Test
    void aRescoringSweepWithDuplicateOrUnknownPathIsRejected() {
        assertFalse(validator
                .validateRequest(rescore("""
                        { "engine": %s, "variantLabel": "baseline", "tableId": "mixed",
                          "sweep": ["reasonPoints.CORRUPTED_OPPONENT_CARD",
                            "reasonPoints.CORRUPTED_OPPONENT_CARD"] }
                        """.formatted(ENGINE)))
                .getMessages()
                .isEmpty());
        assertFalse(validator
                .validateRequest(rescore("""
                        { "engine": %s, "variantLabel": "baseline", "tableId": "mixed",
                          "sweep": ["factions.WEAVERS.winPoints"] }
                        """.formatted(ENGINE)))
                .getMessages()
                .isEmpty());
        assertFalse(validator
                .validateRequest(rescore("""
                        { "engine": %s, "variantLabel": "baseline", "tableId": "mixed",
                          "sweep": [] }
                        """.formatted(ENGINE)))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aRescoringSensitivityResponseValidates() {
        assertValid(validator.validateResponse("/api/v1/runs/{runId}/rescorings", Request.Method.POST, json("""
                { "complete": true,
                  "cells": [ { "playerCount": 4, "faction": "ACTIVISTS", "ratio": 0.97, "lower": 0.9, "upper": 1.04 } ],
                  "playerCounts": [ { "playerCount": 4, "judgedCases": 90, "truncatedCases": 10, "meanEras": 2.2 } ],
                  "sensitivity": [
                    { "parameter": "reasonPoints.CORRUPTED_OPPONENT_CARD", "direction": -1, "status": "EVALUATED",
                      "cells": [ { "playerCount": 4, "faction": "ACTIVISTS", "ratio": 0.9, "delta": -0.07 } ] },
                    { "parameter": "reasonPoints.CORRUPTED_OPPONENT_CARD", "direction": 1, "status": "EVALUATED",
                      "cells": [ { "playerCount": 4, "faction": "ACTIVISTS", "ratio": 1.02, "delta": 0.05 } ] },
                    { "parameter": "escalations.activist-streak.increment", "direction": -1, "status": "EVALUATED",
                      "cells": [ { "playerCount": 4, "faction": "ACTIVISTS", "ratio": 0.95, "delta": -0.02 } ] },
                    { "parameter": "escalations.activist-streak.increment", "direction": 1, "status": "OUT_OF_RANGE",
                      "cells": [] } ] }
                """)));
    }

    @Test
    void rescoringStatesTheSweepRule() {
        var description = openApi.getPaths()
                .get("/api/v1/runs/{runId}/rescorings")
                .getPost()
                .getDescription();

        assertTrue(description.contains("`sweep`"));
        assertTrue(description.contains("`OUT_OF_RANGE`"));
        assertTrue(description.contains("`sweep[i]`"));
        assertTrue(description.contains("-1 then +1"));
        assertTrue(description.contains("reasonPoints.CORRUPTED_OPPONENT_CARD"));
    }

    @Test
    void rescoringStatesTheRecordedPointsRule() {
        var description = openApi.getPaths()
                .get("/api/v1/runs/{runId}/rescorings")
                .getPost()
                .getDescription();

        assertTrue(description.contains("recorded points are not used"));
        assertTrue(description.contains("never counting 0"));
        assertTrue(description.contains("`field` `engine`"));
        assertTrue(description.contains("`TRUNCATED`"));
    }

    @Test
    void rescoringStatesTheEmptyAndUnchangedCasesRules() {
        var description = openApi.getPaths()
                .get("/api/v1/runs/{runId}/rescorings")
                .getPost()
                .getDescription();

        assertTrue(description.contains("null `meanEras`"));
        assertTrue(description.contains("before any case is read"));
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

    private static String request() {
        return request(ENGINE);
    }

    private static String request(String engine) {
        return request(engine, "");
    }

    private static String request(String engine, String sweep) {
        return """
                { "engine": %s, "factions": ["WEAVERS", "ACTIVISTS"],
                  "playerCounts": [3],
                  "rates": [
                    { "playerCount": 3, "faction": "WEAVERS",
                      "betReasons": [ { "scoreReason": "CHAIN_LINK_ADDED", "rate": 0.4 } ],
                      "voidedRate": 0.3,
                      "otherReasons": [ { "scoreReason": "CORRUPTED_OPPONENT_CARD", "perEra": 0.5 } ] },
                    { "playerCount": 3, "faction": "ACTIVISTS",
                      "betReasons": [ { "scoreReason": "DECLARED_OUTCOME_WON", "rate": 0.4 } ],
                      "voidedRate": 0.3,
                      "otherReasons": [ { "scoreReason": "CORRUPTED_OPPONENT_CARD", "perEra": 0.5 } ] } ],
                  "seed": 42%s }
                """.formatted(engine, sweep);
    }

    private static String requestWithSweep(String sweep) {
        return request(ENGINE, sweep);
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
