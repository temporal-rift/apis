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
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;

/** Pins seat tables: the manifest, the cases and cohorts that carry them, and the criterion that names them. */
class SimulationSeatTablesTest {

    private static final String SPECIFICATION = "openapi/v1/simulation.yml";
    private static final String DIGEST = "a".repeat(64);
    private static final String ID = "6f3f2a52-4f0a-4c69-9a39-2a8f0e2b9d11";
    private static final String SEAT_2 =
            "{ \"seatIndex\": 2, \"policyId\": \"faction-greedy\", \"policyVersion\": \"1.0.0\" }";

    private static final String MIXED_MANIFEST = """
            {
              "schemaVersion": 2,
              "name": "random seat against faction-greedy",
              "variants": [ {
                "label": "baseline",
                "rulesArtifact": { "digest": "%1$s" },
                "contentArtifact": { "digest": "%1$s" },
                "effectiveRulesDigest": "%1$s",
                "effectiveContentDigest": "%1$s"
              } ],
              "services": {
                "gameService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" },
                "timelineService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" },
                "readService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" }
              },
              "contracts": { "simulation-api": "5.0.0" },
              "policies": [
                { "id": "random", "version": "1.0.0", "artifactDigest": "%1$s", "parameters": {} },
                { "id": "faction-greedy", "version": "1.0.0", "artifactDigest": "%1$s", "parameters": {} }
              ],
              "tables": [ { "id": "random-seat-0", "seats": [
                { "seatIndex": 0, "policyId": "random", "policyVersion": "1.0.0" },
                { "seatIndex": 1, "policyId": "faction-greedy", "policyVersion": "1.0.0" },
                { "seatIndex": 2, "policyId": "faction-greedy", "policyVersion": "1.0.0" } ] } ],
              "seeds": ["42"],
              "playerCounts": [3],
              "factionSets": [["ERASERS", "ACTIVISTS", "WEAVERS"]],
              "seatRotationMode": "CYCLIC",
              "timingMode": "LOGICAL",
              "concurrency": 1,
              "caseWallTimeoutSeconds": 60,
              "maxRejectedCandidatesPerWindow": 10
            }
            """.formatted(DIGEST);

    private static final String LEGACY_MANIFEST = """
            {
              "schemaVersion": 1,
              "name": "legacy",
              "variants": [ {
                "label": "baseline",
                "rulesArtifact": { "digest": "%1$s" }, "contentArtifact": { "digest": "%1$s" },
                "effectiveRulesDigest": "%1$s", "effectiveContentDigest": "%1$s"
              } ],
              "services": {
                "gameService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" },
                "timelineService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" },
                "readService": { "imageDigest": "sha256:%1$s", "sourceRevision": "abc1234" }
              },
              "contracts": { "simulation-api": "4.5.0" },
              "policies": [ { "id": "random", "version": "v1", "artifactDigest": "%1$s", "parameters": {},
                "seatAssignment": [ { "seatIndex": 0, "policyId": "random", "policyVersion": "v1" } ] } ],
              "seeds": ["42"], "playerCounts": [3],
              "factionSets": [["ERASERS", "ACTIVISTS", "WEAVERS"]],
              "seatRotationMode": "CYCLIC", "timingMode": "LOGICAL",
              "concurrency": 1, "caseWallTimeoutSeconds": 60, "maxRejectedCandidatesPerWindow": 10
            }
            """.formatted(DIGEST);

    private final String url = Objects.requireNonNull(getClass().getClassLoader().getResource(SPECIFICATION))
            .toString();
    private final OpenAPI openApi = new OpenAPIV3Parser().read(url);
    private final OpenApiInteractionValidator validator =
            OpenApiInteractionValidator.createForSpecificationUrl(url).build();

    @Test
    void aMixedTableManifestValidatesForCreationAndPreview() {
        assertValid(validator.validateRequest(post("/api/v1/experiments", MIXED_MANIFEST)));
        assertValid(validator.validateRequest(post("/api/v1/experiment-previews", MIXED_MANIFEST)));
    }

    @Test
    void aRequestManifestMustBeSchemaVersionTwoWithTables() {
        assertInvalid(validator.validateRequest(post(
                "/api/v1/experiments", MIXED_MANIFEST.replace("\"schemaVersion\": 2", "\"schemaVersion\": 1"))));
        assertInvalid(validator.validateRequest(
                post("/api/v1/experiments", MIXED_MANIFEST.replace("\"tables\":", "\"seatTables\":"))));
        assertInvalid(validator.validateRequest(post("/api/v1/experiments", LEGACY_MANIFEST)));
    }

    @Test
    void aPolicyEntryCarriesNoSeatAssignmentAndATableHasThreeToFiveSeats() {
        var bundle = "\"artifactDigest\": \"" + DIGEST + "\", \"parameters\": {} }";

        assertInvalid(validator.validateRequest(post(
                "/api/v1/experiments",
                MIXED_MANIFEST.replaceFirst(
                        java.util.regex.Pattern.quote(bundle),
                        java.util.regex.Matcher.quoteReplacement(
                                bundle.replace(" }", ", \"seatAssignment\": [] }"))))));
        assertInvalid(validator.validateRequest(post(
                "/api/v1/experiments", MIXED_MANIFEST.replace("\"id\": \"random-seat-0\"", "\"id\": \"Random Seat\""))));
        assertInvalid(validator.validateRequest(post(
                "/api/v1/experiments",
                MIXED_MANIFEST.replace(
                        SEAT_2,
                        SEAT_2 + ", " + SEAT_2.replace("2", "3") + ", " + SEAT_2.replace("2", "4") + ", "
                                + SEAT_2.replace("2", "5")))));
    }

    @Test
    void aRefusedTableNamesItsValueInTheProblemField() {
        var problem = """
                { "type": "about:blank", "title": "Bad Request", "status": 400,
                  "detail": "no seat 3 in a 3-player table", "code": "INVALID_EXPERIMENT",
                  "field": "tables[0].seats[3]" }
                """;

        assertValid(validator.validateResponse("/api/v1/experiments", Request.Method.POST, problemJson(400, problem)));
        assertValid(validator.validateResponse(
                "/api/v1/experiments",
                Request.Method.POST,
                problemJson(400, problem.replace("tables[0].seats[3]", "schemaVersion"))));
    }

    @Test
    void aStoredManifestIsReturnedAsFrozenWhateverItsSchemaVersion() {
        assertValid(validator.validateResponse(
                "/api/v1/experiments/" + ID, Request.Method.GET, json(experiment(MIXED_MANIFEST))));
        assertValid(validator.validateResponse(
                "/api/v1/experiments/" + ID, Request.Method.GET, json(experiment(LEGACY_MANIFEST))));
        assertInvalid(validator.validateResponse(
                "/api/v1/experiments/" + ID,
                Request.Method.GET,
                json(experiment(LEGACY_MANIFEST.replace("\"schemaVersion\": 1", "\"schemaVersion\": 3")))));
    }

    @Test
    void cohortsCarryATableAndAMixedCohortHasNoSinglePolicy() throws IOException {
        var report = fixture("report-shared-win.json");
        var mixed = report.replace("\"policyId\": \"random\",", "\"policyId\": null,")
                .replace("\"policyVersion\": \"1.0.0\",", "\"policyVersion\": null,");

        assertValid(validator.validateResponse("/api/v1/runs/" + ID + "/report", Request.Method.GET, json(report)));
        assertValid(validator.validateResponse("/api/v1/runs/" + ID + "/report", Request.Method.GET, json(mixed)));
        assertInvalid(validator.validateResponse(
                "/api/v1/runs/" + ID + "/report",
                Request.Method.GET,
                json(report.replace("\"tableId\": \"random\",", ""))));
        assertInvalid(validator.validateResponse(
                "/api/v1/runs/" + ID + "/report",
                Request.Method.GET,
                json(report.replace("\"formatVersion\": 3", "\"formatVersion\": 2"))));
    }

    @Test
    void aComparisonPairNamesItsTableAndRunsWithDifferentTablesAreIncomparable() throws IOException {
        var comparison = fixture("comparison-failed-counterpart.json");

        assertValid(validator.validateResponse("/api/v1/comparisons/" + ID, Request.Method.GET, json(comparison)));
        assertInvalid(validator.validateResponse(
                "/api/v1/comparisons/" + ID,
                Request.Method.GET,
                json(comparison.replace("\"tableId\": \"random\",", ""))));
        assertValid(validator.validateResponse(
                "/api/v1/comparisons",
                Request.Method.POST,
                problemJson(422, """
                        { "type": "about:blank", "title": "Unprocessable", "status": 422,
                          "detail": "the runs differ in: tables", "code": "INCOMPARABLE_RUNS" }
                        """)));
    }

    @Test
    void criterionVersionOneHasTwoUniformTablesAndLinesNamePopulations() throws IOException {
        var criterion = fixture("balance-criterion-v1.json");

        assertTrue(criterion.contains("\"id\": \"faction-greedy-v1\""));
        assertTrue(criterion.contains("\"id\": \"random-v1\""));
        assertTrue(criterion.contains("\"populations\": [ { \"table\": \"random-v1\", \"policy\": \"random-v1\" } ]"));
        assertFalse(criterion.contains("\"policies\""));
        assertValid(validator.validateResponse("/api/v1/balance-criteria/1", Request.Method.GET, json(criterion)));
        assertInvalid(validator.validateResponse(
                "/api/v1/balance-criteria/1",
                Request.Method.GET,
                json(criterion.replace("\"populations\"", "\"policies\""))));
        assertInvalid(validator.validateResponse(
                "/api/v1/balance-criteria/1",
                Request.Method.GET,
                json(criterion.replace("\"phases\": [ \"SCREENING\", \"CONFIRMATION\" ]", "\"phases\": []"))));
    }

    @Test
    void aCampaignPlaysCriterionTablesSoItsPoliciesAreBundleReferencesAndOptional() {
        var schemas = openApi.getComponents().getSchemas();

        assertFalse(schemas.get("PolicyBundleReference").getProperties().containsKey("seatAssignment"));
        assertFalse(schemas.get("CampaignDefinition").getRequired().contains("policies"));
        assertTrue(schemas.get("Campaign").getRequired().contains("policies"));
        List.of("CampaignDefinition", "Campaign").forEach(name -> assertEquals(
                "#/components/schemas/PolicyBundleReference",
                ((Schema<?>) schemas.get(name).getProperties().get("policies")).getItems().get$ref()));
    }

    @Test
    void startConfirmationRefusesWithTheSimulatorAndPolicyCodesButNeverOverFidelity() {
        var path = "/api/v1/campaigns/3c1c4a7e-8b2d-4f46-9a71-5d0e2b6c8f91/confirmation";

        List.of(
                        "IDEMPOTENCY_CONFLICT",
                        "INVALID_CAMPAIGN_STATE",
                        "SEED_OVERLAP",
                        "SIMULATOR_NOT_AVAILABLE",
                        "POLICY_NOT_AVAILABLE")
                .forEach(code -> assertValid(validator.validateResponse(
                        path, Request.Method.POST, problemJson(409, conflict(code)))));
        assertInvalid(validator.validateResponse(
                path, Request.Method.POST, problemJson(409, conflict("FIDELITY_NOT_ESTABLISHED"))));
        assertInvalid(validator.validateResponse(path, Request.Method.POST, problemJson(409, conflict("BOGUS"))));
    }

    private static String conflict(String code) {
        return """
                { "type": "about:blank", "title": "Conflict", "status": 409, "code": "%s" }
                """.formatted(code);
    }

    private static String experiment(String manifest) {
        return """
                { "experimentId": "%s", "manifest": %s, "manifestDigest": "%s",
                  "createdAt": "2026-10-09T12:00:00Z" }
                """.formatted(ID, manifest, DIGEST);
    }

    private static Request post(String path, String body) {
        return SimpleRequest.Builder.post(path)
                .withHeader("Authorization", "Bearer token")
                .withHeader("Idempotency-Key", "3f2b1c0d-9e8f-4a7b-8c6d-5e4f3a2b1c0d")
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

    private static void assertInvalid(ValidationReport report) {
        assertFalse(report.getMessages().isEmpty());
    }

    private String fixture(String name) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/" + name)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
