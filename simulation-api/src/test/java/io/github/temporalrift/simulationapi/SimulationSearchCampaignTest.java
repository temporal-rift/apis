package io.github.temporalrift.simulationapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.parser.OpenAPIV3Parser;

/** Validates the search campaign shapes and published examples as requests and responses. */
class SimulationSearchCampaignTest {

    private static final String SPECIFICATION = "openapi/v1/simulation.yml";
    private static final String CAMPAIGN_ID = "3c1c4a7e-8b2d-4f46-9a71-5d0e2b6c8f91";
    private static final String RULES_DIGEST = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
    private static final String CONTENT_DIGEST = "60303ae22b998861bce3b28f33eec1be758a213c86c93c076dbe9f558c11c752";
    private static final String POLICY_DIGEST = "5a1c1e2d3f405162738495a6b7c8d9e0f1a2b3c4d5e6f708192a3b4c5d6e7f80";
    private static final String IDEMPOTENCY_KEY = "3f2b1c0d-9e8f-4a7b-8c6d-5e4f3a2b1c0d";

    private final OpenApiInteractionValidator validator = OpenApiInteractionValidator.createForSpecificationUrl(
                    Objects.requireNonNull(getClass().getClassLoader().getResource(SPECIFICATION))
                            .toString())
            .build();
    private final OpenAPI openApi = new OpenAPIV3Parser().read(Objects.requireNonNull(
                    getClass().getClassLoader().getResource(SPECIFICATION))
            .toString());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aDefinitionWithoutBudgetOrParallelismIsAccepted() {
        assertValid(validator.validateRequest(createCampaign("""
                {
                  "name": "threshold-halving-01",
                  "baseline": {
                    "rulesArtifact": { "digest": "%s" },
                    "contentArtifact": { "digest": "%s" }
                  },
                  "parameterSpaceVersion": 1,
                  "criterionVersion": 1,
                  "policies": [ {
                    "id": "faction-greedy",
                    "version": "1.0.0",
                    "artifactDigest": "%s",
                    "parameters": {},
                    "seatAssignment": [
                      { "seatIndex": 0, "policyId": "faction-greedy", "policyVersion": "1.0.0" },
                      { "seatIndex": 1, "policyId": "faction-greedy", "policyVersion": "1.0.0" },
                      { "seatIndex": 2, "policyId": "faction-greedy", "policyVersion": "1.0.0" },
                      { "seatIndex": 3, "policyId": "faction-greedy", "policyVersion": "1.0.0" }
                    ]
                  } ],
                  "playerCounts": [ 4 ],
                  "searchMasterSeed": "42",
                  "confirmationMasterSeed": "43",
                  "algorithm": {
                    "version": "SUCCESSIVE_HALVING_V1",
                    "initialCandidates": 8,
                    "screeningSeeds": 2,
                    "eta": 2,
                    "shortListSize": 2
                  }
                }
                """.formatted(RULES_DIGEST, CONTENT_DIGEST, POLICY_DIGEST))));
    }

    @Test
    void thePublishedPausedCampaignExampleValidates() {
        var example = example("/api/v1/campaigns/{campaignId}", "get", "200", "paused");
        var body = toJson(example);

        assertTrue(body.contains("\"state\":\"PAUSED\""));
        assertValid(validator.validateResponse(
                "/api/v1/campaigns/" + CAMPAIGN_ID, Request.Method.GET, json(200, body)));
    }

    @Test
    void thePublishedDiscardedLogEntryExampleValidates() {
        var example = example("/api/v1/campaigns/{campaignId}/log", "get", "200", "gate-failure");
        var body = toJson(example);

        assertTrue(body.contains("\"decision\":\"DISCARDED\""));
        assertTrue(body.contains("\"reason\":\"GATE_FAILS\""));
        assertTrue(body.contains("\"lineIds\":[\"G-01\"]"));
        assertValid(validator.validateResponse(
                "/api/v1/campaigns/" + CAMPAIGN_ID + "/log", Request.Method.GET, json(200, body)));
    }

    @Test
    void equalMasterSeedsAreDocumentedAsAnInvalidCampaign() {
        var problem = """
                {
                  "type": "https://temporal-rift.io/problems/invalid-campaign",
                  "title": "Invalid campaign",
                  "status": 400,
                  "code": "INVALID_CAMPAIGN",
                  "field": "confirmationMasterSeed",
                  "reason": "EQUAL_MASTER_SEEDS"
                }
                """;

        assertValid(validator.validateResponse(
                "/api/v1/campaigns", Request.Method.POST, problemJson(400, problem)));
    }

    private Example example(String path, String method, String status, String name) {
        var operation = "get".equals(method)
                ? openApi.getPaths().get(path).getGet()
                : openApi.getPaths().get(path).getPost();
        var examples = operation.getResponses().get(status).getContent().get("application/json").getExamples();
        var example = examples == null ? null : examples.get(name);
        assertNotNull(example, "Missing published example " + name);
        return example;
    }

    private String toJson(Example example) {
        try {
            return objectMapper.writeValueAsString(example.getValue());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Request createCampaign(String body) {
        return SimpleRequest.Builder.post("/api/v1/campaigns")
                .withHeader("Authorization", "Bearer token")
                .withHeader("Idempotency-Key", IDEMPOTENCY_KEY)
                .withContentType("application/json")
                .withBody(body)
                .build();
    }

    private static SimpleResponse json(int status, String body) {
        return SimpleResponse.Builder.status(status)
                .withContentType("application/json")
                .withBody(body)
                .build();
    }

    private static SimpleResponse problemJson(int status, String body) {
        return SimpleResponse.Builder.status(status)
                .withContentType("application/problem+json")
                .withBody(body)
                .build();
    }

    private static void assertValid(ValidationReport report) {
        assertTrue(report.getMessages().isEmpty(), report::toString);
    }
}
