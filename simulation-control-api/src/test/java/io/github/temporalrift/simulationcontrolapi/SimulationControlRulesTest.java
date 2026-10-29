package io.github.temporalrift.simulationcontrolapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;

class SimulationControlRulesTest {

    private static final String SPECIFICATION = "openapi/v1/simulation-control.yml";
    private static final String EXECUTION_PATH = "/internal/simulation/v1/execution";
    private static final String EXAMPLE_DIGEST =
            "2f4e77d6f2f5ad41eb3b05de091e524133995ef6f274a58f539adbf2d5b06fad";

    private final String url = Objects.requireNonNull(getClass().getClassLoader().getResource(SPECIFICATION))
            .toString();
    private final OpenAPI openApi = new OpenAPIV3Parser().read(url);
    private final OpenApiInteractionValidator validator =
            OpenApiInteractionValidator.createForSpecificationUrl(url).build();

    @Test
    void thePublishedContextExampleHasItsPublishedRulesDigest() throws NoSuchAlgorithmException {
        var context = openApi.getComponents().getSchemas().get("ExecutionContext");
        assertNotNull(context.getExample());
        var exampleNode = new ObjectMapper().valueToTree(context.getExample());
        @SuppressWarnings("unchecked")
        var example = (Map<String, Object>) new ObjectMapper().convertValue(exampleNode, Map.class);
        @SuppressWarnings("unchecked")
        var rules = (Map<String, Object>) example.get("rules");
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) rules.get("properties");

        var canonical = canonicalProperties(properties);
        assertEquals(
                "{\"game.rules.probability.amplify-multiplier.II\":2,\"game.rules.win-score-threshold\":20}",
                canonical);
        var digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        var computed = HexFormat.of().formatHex(digest);

        assertEquals(EXAMPLE_DIGEST, computed);
        assertEquals(computed, example.get("rulesDigest"));
    }

    @Test
    void aContextWithoutRulesFailsSchemaValidation() {
        assertFalse(validator.validateRequest(request(contextWithoutRules())).getMessages().isEmpty());
    }

    @Test
    void aContextWithMatchingRulesAndDigestValidates() {
        var report = validator.validateRequest(request(contextWithRules()));
        assertTrue(report.getMessages().isEmpty(), report::toString);
    }

    @Test
    void theSharedEffectiveRulesResourceIsBundledBesideTheSpecification() {
        assertNotNull(getClass().getClassLoader().getResource("openapi/shared/effective-rules.yaml"));
        assertTrue(specificationText().contains("$ref: '../shared/effective-rules.yaml#/EffectiveRules'"));
    }

    @Test
    void closeUsesARequiredUuidQueryParameterAndAnswersNoContent() {
        var operation = openApi.getPaths().get(EXECUTION_PATH).getDelete();

        assertEquals("closeSimulationExecution", operation.getOperationId());
        assertNotNull(operation.getParameters());
        var caseKey = operation.getParameters().stream()
                .filter(parameter -> "caseKey".equals(parameter.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("query", caseKey.getIn());
        assertTrue(caseKey.getRequired());
        assertEquals("uuid", caseKey.getSchema().getFormat());
        assertNotNull(operation.getResponses().get("204"));
        assertNotNull(operation.getResponses().get("409"));
    }

    @Test
    void configureAnswersItsConflictWithTheSharedNamedResponse() {
        var operation = openApi.getPaths().get(EXECUTION_PATH).getPut();
        var conflict = operation.getResponses().get("409");

        assertEquals("#/components/responses/ExecutionContextConflict", conflict.get$ref());
        assertNotNull(openApi.getComponents().getResponses().get("ExecutionContextConflict"));
    }

    @Test
    void everyNamedResponseIsReferenced() {
        var text = specificationText();

        openApi.getComponents().getResponses().keySet().forEach(name ->
                assertTrue(text.contains("#/components/responses/" + name), () -> "Unused response " + name));
    }

    private Request request(String body) {
        return new SimpleRequest.Builder(Request.Method.PUT, EXECUTION_PATH)
                .withHeader("Content-Type", "application/json")
                .withBody(body)
                .build();
    }

    private String specificationText() {
        try (var stream = getClass().getClassLoader().getResourceAsStream(SPECIFICATION)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String canonicalProperties(Map<String, Object> properties) {
        var sorted = new TreeMap<>(properties);
        var json = new StringBuilder("{");
        var first = true;
        for (var entry : sorted.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append('"').append(':').append(canonicalValue(entry.getValue()));
        }
        return json.append('}').toString();
    }

    private String canonicalValue(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Boolean bool) {
            return bool.toString();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalValue).reduce((left, right) -> left + "," + right)
                    .map(joined -> "[" + joined + "]")
                    .orElse("[]");
        }
        return "\"" + value + "\"";
    }

    private String contextWithoutRules() {
        return """
                {
                  "schemaVersion": 1,
                  "caseKey": "6f3f2a52-4f0a-4c69-9a39-2a8f0e2b9d11",
                  "seed": "42",
                  "entropyVersion": "SHA256_V1",
                  "manifestDigest": "%1$s",
                  "rulesDigest": "%2$s",
                  "logicalTime": "2026-10-05T19:00:00Z",
                  "seats": %3$s
                }
                """.formatted("a".repeat(64), EXAMPLE_DIGEST, seats());
    }

    private String contextWithRules() {
        return """
                {
                  "schemaVersion": 1,
                  "caseKey": "6f3f2a52-4f0a-4c69-9a39-2a8f0e2b9d11",
                  "seed": "42",
                  "entropyVersion": "SHA256_V1",
                  "manifestDigest": "%1$s",
                  "rules": {
                    "schemaVersion": 1,
                    "properties": {
                      "game.rules.probability.amplify-multiplier.II": 2,
                      "game.rules.win-score-threshold": 20
                    }
                  },
                  "rulesDigest": "%2$s",
                  "logicalTime": "2026-10-05T19:00:00Z",
                  "seats": %3$s
                }
                """.formatted("a".repeat(64), EXAMPLE_DIGEST, seats());
    }

    private String seats() {
        return """
                [
                  { "seatIndex": 0, "playerId": "0d9b6f5e-1f4a-4c62-9a3c-2d8f0e2b9d11", "faction": "ERASERS" },
                  { "seatIndex": 1, "playerId": "1e8c7a6f-2b5a-4d73-8b4d-3e9f1a3c5d22", "faction": "WEAVERS" },
                  { "seatIndex": 2, "playerId": "2f9d8b7a-3c6b-4e84-9c5e-4fa02b4d6f33", "faction": "PROPHETS" }
                ]
                """.strip();
    }
}
