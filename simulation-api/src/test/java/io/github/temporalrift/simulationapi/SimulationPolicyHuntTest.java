package io.github.temporalrift.simulationapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

/** Validates the policy hunt shapes: the policy parameter space reads, the hunt campaign and its report. */
class SimulationPolicyHuntTest {

    private static final String SPECIFICATION = "openapi/v1/simulation.yml";
    private static final String RULES_DIGEST = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
    private static final String CONTENT_DIGEST = "60303ae22b998861bce3b28f33eec1be758a213c86c93c076dbe9f558c11c752";
    private static final String IDEMPOTENCY_KEY = "3f2b1c0d-9e8f-4a7b-8c6d-5e4f3a2b1c0d";

    private final OpenApiInteractionValidator validator = OpenApiInteractionValidator.createForSpecificationUrl(
                    Objects.requireNonNull(getClass().getClassLoader().getResource(SPECIFICATION))
                            .toString())
            .build();

    @Test
    void thePolicyParameterSpaceCatalogIsServed() {
        assertValid(validator.validateResponse("/api/v1/policy-parameter-spaces", Request.Method.GET, json(200, """
                { "items": [ %s ] }
                """.formatted(space()))));
    }

    @Test
    void onePolicyParameterSpaceIsServedAndAnUnknownOneIsNotFound() {
        assertValid(validator.validateResponse(
                "/api/v1/policy-parameter-spaces/{version}", Request.Method.GET, json(200, space())));
        assertValid(validator.validateResponse(
                "/api/v1/policy-parameter-spaces/{version}",
                Request.Method.GET,
                SimpleResponse.Builder.status(404)
                        .withContentType("application/problem+json")
                        .withBody(
                                """
                        { "type": "about:blank", "title": "Not found", "status": 404, "code": "RESOURCE_NOT_FOUND" }
                        """)
                        .build()));
    }

    @Test
    void aSubsetOutsideTwelveToSixteenParametersIsRefused() {
        assertFalse(validator
                .validateResponse(
                        "/api/v1/policy-parameter-spaces/{version}",
                        Request.Method.GET,
                        json(
                                200,
                                """
                        { "version": 1, "policyId": "faction-search", "policyVersion": "1.0.0",
                          "parameters": [ { "key": "k", "defaultValue": 1, "min": 0, "max": 8, "step": 1 } ],
                          "searchedSubsets": [ { "faction": "WEAVERS", "keys": [ "k" ] } ] }
                        """))
                .getMessages()
                .isEmpty());
    }

    @Test
    void aHuntDefinitionNamesAFactionAndOnePlayerCount() {
        assertValid(validator.validateRequest(createCampaign("""
                {
                  "name": "weavers-5p",
                  "kind": "POLICY_HUNT",
                  "faction": "WEAVERS",
                  "policyParameterSpaceVersion": 1,
                  "baseline": {
                    "rulesArtifact": { "digest": "%s" },
                    "contentArtifact": { "digest": "%s" }
                  },
                  "playerCounts": [ 5 ]
                }
                """.formatted(RULES_DIGEST, CONTENT_DIGEST))));
    }

    @Test
    void aDefinitionWithoutAKindStillValidates() {
        assertValid(validator.validateRequest(createCampaign("""
                {
                  "name": "rules-01",
                  "baseline": {
                    "rulesArtifact": { "digest": "%s" },
                    "contentArtifact": { "digest": "%s" }
                  },
                  "playerCounts": [ 4 ]
                }
                """.formatted(RULES_DIGEST, CONTENT_DIGEST))));
    }

    @Test
    void aBundleListsItsParameterDefinitions() {
        assertValid(validator.validateResponse("/api/v1/policies", Request.Method.GET, json(200, """
                { "items": [ { "id": "faction-search", "version": "1.0.0",
                               "artifactDigest": "%s", "description": "faction-spread with parameters",
                               "parameters": {},
                               "parameterDefinitions": [
                                 { "key": "affinity.card.WEAVERS.SCAN", "defaultValue": 1, "min": 0, "max": 8,
                                   "step": 1 } ] } ] }
                """.formatted(RULES_DIGEST))));
    }

    private static String space() {
        var keys = new StringBuilder();
        for (var i = 0; i < 12; i++) {
            keys.append(i == 0 ? "" : ",").append("\"k").append(i).append('"');
        }
        return """
                { "version": 1, "policyId": "faction-search", "policyVersion": "1.0.0",
                  "parameters": [ { "key": "k0", "defaultValue": 1, "min": 0, "max": 8, "step": 1 } ],
                  "searchedSubsets": [ { "faction": "WEAVERS", "keys": [ %s ] } ] }
                """.formatted(keys);
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

    private static void assertValid(ValidationReport report) {
        assertTrue(report.getMessages().isEmpty(), report::toString);
    }
}
