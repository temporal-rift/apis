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
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;

/** Pins the operations that start a simulator fidelity check, serve its record and gate on it. */
class SimulationFidelityTest {

    private static final String SPECIFICATION = "openapi/v1/simulation.yml";
    private static final String DIGEST = "a".repeat(64);
    private static final String IMAGE = "sha256:" + DIGEST;
    private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";
    private static final String ID = "6f3f2a52-4f0a-4c69-9a39-2a8f0e2b9d11";
    private static final String KEY = "0b5c2f0e-2f57-4b8e-9a64-3f0d6a8f7e10";

    private final String url = Objects.requireNonNull(getClass().getClassLoader().getResource(SPECIFICATION))
            .toString();
    private final OpenAPI openApi = new OpenAPIV3Parser().read(url);
    private final OpenApiInteractionValidator validator =
            OpenApiInteractionValidator.createForSpecificationUrl(url).build();

    @Test
    void everyFidelityOperationDeclaresItsScope() {
        var scopes = Map.of(
                "/api/v1/fidelity-checks/{fidelityCheckId}", List.of("simulation:read"),
                "/api/v1/fidelity-checks/{fidelityCheckId}/cases", List.of("simulation:read"),
                "/api/v1/simulator/fidelity", List.of("simulation:read"));

        scopes.forEach((path, expected) -> assertEquals(
                expected,
                openApi.getPaths().get(path).getGet().getExtensions().get("x-required-scopes"),
                path));
        var collection = openApi.getPaths().get("/api/v1/fidelity-checks");
        assertEquals(List.of("simulation:write"), collection.getPost().getExtensions().get("x-required-scopes"));
        assertEquals(List.of("simulation:read"), collection.getGet().getExtensions().get("x-required-scopes"));
    }

    @Test
    void aCheckIsStartedWithOnlyASeedAndAnIdempotencyKey() {
        assertValid(validator.validateRequest(start("{ \"seed\": \"42\" }", true)));
        assertFalse(validator.validateRequest(start("{ \"seed\": \"-1\" }", true)).getMessages().isEmpty());
        assertFalse(validator.validateRequest(start("{ \"seed\": 42 }", true)).getMessages().isEmpty());
        assertFalse(validator.validateRequest(start("{}", true)).getMessages().isEmpty());
        assertFalse(validator
                .validateRequest(start("{ \"seed\": \"42\", \"variants\": [] }", true))
                .getMessages()
                .isEmpty());
        assertFalse(validator.validateRequest(start("{ \"seed\": \"42\" }", false)).getMessages().isEmpty());
    }

    @Test
    void aPassedCheckCountsEveryCaseAsMatched() {
        var check = check("PASSED", "\"PASSED\"", "{ \"total\": 330, \"matched\": 330, \"diverged\": 0, "
                + "\"unreproducible\": 0, \"pending\": 0 }");

        assertValid(validator.validateResponse(
                "/api/v1/fidelity-checks",
                Request.Method.POST,
                accepted(check(
                        "QUEUED",
                        "null",
                        "{ \"total\": 330, \"matched\": 0, \"diverged\": 0, \"unreproducible\": 0, \"pending\": 330 }"))));
        assertValid(validator.validateResponse(
                "/api/v1/fidelity-checks/" + ID, Request.Method.GET, json(check)));
        assertValid(validator.validateResponse(
                "/api/v1/fidelity-checks", Request.Method.GET, json("{ \"items\": [" + check + "], \"total\": 1 }")));
    }

    @Test
    void aCheckNamesBothSidesWithTheirSourceRevisionsAndImageDigests() {
        var environment = openApi.getComponents().getSchemas().get("FidelityEnvironment");
        var provenance = openApi.getComponents().getSchemas().get("ServiceProvenance");

        assertEquals(java.util.Set.of("services", "contentDigest", "contracts"), java.util.Set.copyOf(environment.getRequired()));
        assertEquals(java.util.Set.of("imageDigest", "sourceRevision"), java.util.Set.copyOf(provenance.getRequired()));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/fidelity-checks/" + ID,
                        Request.Method.GET,
                        json(check("RUNNING", "null", counts()).replace("\"sourceRevision\": \"" + REVISION + "\"", "")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aFailedCheckKeepsItsFirstDivergence() {
        var check = check("FAILED", "\"FAILED\"", "{ \"total\": 330, \"matched\": 329, \"diverged\": 1, "
                + "\"unreproducible\": 0, \"pending\": 0 }");
        var cases = """
                { "items": [ {
                    "caseKey": "%s", "variantLabel": "high", "playerCount": 4,
                    "factionSet": [ "ERASERS", "ACTIVISTS", "WEAVERS", "PROPHETS" ],
                    "seatRotation": 2, "policyId": "random-v1", "result": "DIVERGED",
                    "firstDivergence": { "step": 17, "kind": "OBSERVATION",
                        "expected": { "timers": [ "a", "b" ] }, "actual": { "timers": [ "b", "a" ] } },
                    "failure": null } ],
                  "total": 1 }
                """.formatted(ID);

        assertValid(validator.validateResponse("/api/v1/fidelity-checks/" + ID, Request.Method.GET, json(check)));
        assertValid(validator.validateResponse(
                "/api/v1/fidelity-checks/" + ID + "/cases", Request.Method.GET, json(cases)));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/fidelity-checks/" + ID + "/cases",
                        Request.Method.GET,
                        json(cases.replace("\"DIVERGED\"", "\"UNKNOWN\"")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void anUnreproducibleCaseCarriesItsFailure() {
        var cases = """
                { "items": [ {
                    "caseKey": "%s", "variantLabel": "low", "playerCount": 3,
                    "factionSet": [ "ERASERS", "ACTIVISTS", "WEAVERS" ],
                    "seatRotation": 0, "policyId": "faction-greedy-v1", "result": "UNREPRODUCIBLE",
                    "firstDivergence": null,
                    "failure": { "code": "RUNNER_TIMEOUT", "message": "The lane did not finish the game" } } ],
                  "total": 1 }
                """.formatted(ID);

        assertValid(validator.validateResponse(
                "/api/v1/fidelity-checks/" + ID + "/cases", Request.Method.GET, json(cases)));
    }

    @Test
    void theVerdictRuleAndTheStatesAreDocumented() {
        var schemas = openApi.getComponents().getSchemas();

        assertTrue(schemas.get("FidelityCheck").getDescription().contains("`counts.matched` equals `counts.total`"));
        assertTrue(schemas.get("FidelityCheck").getDescription().contains("No tolerance exists"));
        assertEquals(List.of("QUEUED", "RUNNING", "PASSED", "FAILED"), enumOf(schemas.get("FidelityCheckState")));
        assertEquals(
                List.of("PENDING", "MATCH", "DIVERGED", "UNREPRODUCIBLE"), enumOf(schemas.get("FidelityCaseResult")));
        assertEquals(List.of("baseline", "low", "high"), enumOf(schemas.get("FidelityVariantLabel")));
    }

    @Test
    void theGateAnswersNotEstablishedWithoutARecord() {
        var gate = """
                { "sourceRevisions": { "gameService": "%s", "timelineService": "%s", "readService": "%s" },
                  "established": false, "record": null }
                """.formatted(REVISION, REVISION, REVISION);

        assertValid(validator.validateResponse("/api/v1/simulator/fidelity", Request.Method.GET, json(gate)));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/simulator/fidelity",
                        Request.Method.GET,
                        json(gate.replace("\"established\": false,", "")))
                .getMessages()
                .isEmpty());
    }

    @Test
    void theGateCarriesTheNewestPassingRecord() {
        var gate = """
                { "sourceRevisions": { "gameService": "%s", "timelineService": "%s", "readService": "%s" },
                  "established": true, "record": %s }
                """.formatted(
                REVISION, REVISION, REVISION, check("PASSED", "\"PASSED\"", counts()));

        assertValid(validator.validateResponse("/api/v1/simulator/fidelity", Request.Method.GET, json(gate)));
    }

    @Test
    void refusalsUseTheirDocumentedCodes() {
        var codes = enumOf((Schema<?>) openApi.getComponents().getSchemas().get("ProblemDetail")
                .getProperties().get("code"));

        assertTrue(codes.containsAll(List.of("FIDELITY_CHECK_ACTIVE", "SIMULATOR_NOT_AVAILABLE")));
        List.of("FIDELITY_CHECK_ACTIVE", "SIMULATOR_NOT_AVAILABLE", "IDEMPOTENCY_CONFLICT")
                .forEach(code -> assertValid(validator.validateResponse(
                        "/api/v1/fidelity-checks", Request.Method.POST, problemJson(409, problem(409, code)))));
        assertValid(validator.validateResponse(
                "/api/v1/simulator/fidelity",
                Request.Method.GET,
                problemJson(409, problem(409, "SIMULATOR_NOT_AVAILABLE"))));
        assertValid(validator.validateResponse(
                "/api/v1/fidelity-checks/" + ID, Request.Method.GET, problemJson(404, problem(404, "RESOURCE_NOT_FOUND"))));
        assertFalse(validator
                .validateResponse(
                        "/api/v1/fidelity-checks",
                        Request.Method.POST,
                        problemJson(409, problem(409, "FIDELITY_UNKNOWN")))
                .getMessages()
                .isEmpty());
    }

    private static String counts() {
        return "{ \"total\": 330, \"matched\": 330, \"diverged\": 0, \"unreproducible\": 0, \"pending\": 0 }";
    }

    private static String environment() {
        var build = "{ \"imageDigest\": \"%s\", \"sourceRevision\": \"%s\" }".formatted(IMAGE, REVISION);
        return """
                { "services": { "gameService": %1$s, "timelineService": %1$s, "readService": %1$s },
                  "contentDigest": "%2$s", "contracts": { "session-api": "1.0.0" } }
                """.formatted(build, DIGEST);
    }

    private static String check(String state, String verdict, String counts) {
        return """
                { "fidelityCheckId": "%s", "seed": "42", "state": "%s", "verdict": %s,
                  "simulator": %s, "lane": %s,
                  "variants": [
                    { "label": "baseline", "effectiveRulesDigest": "%s", "overrides": {} },
                    { "label": "low", "effectiveRulesDigest": "%s",
                      "overrides": { "game.rules.win-score-threshold": 16 } },
                    { "label": "high", "effectiveRulesDigest": "%s",
                      "overrides": { "game.rules.win-score-threshold": 26 } } ],
                  "counts": %s, "failure": null,
                  "createdAt": "2026-10-27T10:00:00Z", "startedAt": "2026-10-27T10:00:05Z", "finishedAt": null }
                """.formatted(ID, state, verdict, environment(), environment(), DIGEST, DIGEST, DIGEST, counts);
    }

    private static String problem(int status, String code) {
        return """
                { "type": "about:blank", "title": "Problem", "status": %d, "code": "%s" }
                """.formatted(status, code);
    }

    private static List<?> enumOf(Schema<?> schema) {
        return schema.getEnum();
    }

    private static Request start(String body, boolean withKey) {
        var builder = SimpleRequest.Builder.post("/api/v1/fidelity-checks")
                .withHeader("Authorization", "Bearer token")
                .withContentType("application/json")
                .withBody(body);
        return (withKey ? builder.withHeader("Idempotency-Key", KEY) : builder).build();
    }

    private static SimpleResponse json(String body) {
        return SimpleResponse.Builder.ok()
                .withContentType("application/json")
                .withBody(body)
                .build();
    }

    private static SimpleResponse accepted(String body) {
        return SimpleResponse.Builder.status(202)
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
