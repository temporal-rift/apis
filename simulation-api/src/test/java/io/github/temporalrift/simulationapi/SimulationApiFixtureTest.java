package io.github.temporalrift.simulationapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import io.swagger.v3.parser.OpenAPIV3Parser;

/** Validates report and comparison fixtures as real requests and responses of the published operations. */
class SimulationApiFixtureTest {

    private static final String SPECIFICATION = "openapi/v1/simulation.yml";
    private static final String RUN_ID = "6f3f2a52-4f0a-4c69-9a39-2a8f0e2b9d11";
    private static final String COMPARISON_ID = "0c2b8f1e-7d3a-4e55-8b8e-1f7a9c3d2e44";

    private final OpenApiInteractionValidator validator = OpenApiInteractionValidator.createForSpecificationUrl(
                    Objects.requireNonNull(getClass().getClassLoader().getResource(SPECIFICATION))
                            .toString())
            .build();

    @Test
    void aSharedWinCountsForEveryWinnerWithExplicitStatisticAndDimensions() throws IOException {
        var report = fixture("report-shared-win.json");

        assertValid(validator.validateResponse("/api/v1/runs/" + RUN_ID + "/report", Request.Method.GET, json(report)));
        assertTrue(report.contains("\"name\": \"faction_shared_win_rate\""));
    }

    @Test
    void aMetricDimensionOutsideTheVocabularyIsRejected() throws IOException {
        var report = fixture("report-shared-win.json").replace("{ \"faction\": \"ERASERS\" }", "{ \"player\": \"0\" }");

        assertFalse(validator
                .validateResponse("/api/v1/runs/" + RUN_ID + "/report", Request.Method.GET, json(report))
                .getMessages()
                .isEmpty());
    }

    @Test
    void anIntervalIsMandatoryEvenWhenItIsNull() throws IOException {
        var report = fixture("report-shared-win.json").replace("\"interval\": null,", "");

        assertFalse(validator
                .validateResponse("/api/v1/runs/" + RUN_ID + "/report", Request.Method.GET, json(report))
                .getMessages()
                .isEmpty());
    }

    @Test
    void bothSidesMayNameTheSameRunWithDifferentVariants() {
        var body = """
                {
                  "baseline": { "runId": "%s", "variantLabel": "threshold-20" },
                  "candidate": { "runId": "%s", "variantLabel": "threshold-22" }
                }
                """.formatted(RUN_ID, RUN_ID);

        assertValid(validator.validateRequest(createComparison(body)));
    }

    @Test
    void theRunOnlyRequestShapeIsNoLongerAccepted() {
        var body = """
                { "baselineRunId": "%s", "candidateRunId": "%s" }
                """.formatted(RUN_ID, RUN_ID);

        assertFalse(validator.validateRequest(createComparison(body)).getMessages().isEmpty());
    }

    @Test
    void aFailedCounterpartIsListedAndThePartialComparisonSaysSo() throws IOException {
        var comparison = fixture("comparison-failed-counterpart.json");

        assertValid(validator.validateResponse(
                "/api/v1/comparisons/" + COMPARISON_ID, Request.Method.GET, json(comparison)));
        assertTrue(comparison.contains("\"reason\": \"FAILED_COUNTERPART\""));
        assertTrue(comparison.contains("\"complete\": false"));
    }

    @Test
    void reportsAndComparisonsAlsoExportAsCsv() {
        var openApi = new OpenAPIV3Parser().read(Objects.requireNonNull(getClass().getClassLoader().getResource(
                        SPECIFICATION))
                .toString());
        var report = openApi.getPaths().get("/api/v1/runs/{runId}/report").getGet();
        var comparison = openApi.getPaths().get("/api/v1/comparisons/{comparisonId}").getGet();

        assertEquals(
                Set.of("application/json", "text/csv"),
                report.getResponses().get("200").getContent().keySet());
        assertEquals(
                Set.of("application/json", "text/csv"),
                comparison.getResponses().get("200").getContent().keySet());
        assertEquals(java.util.List.of("Reports"), report.getTags());
    }

    private static Request createComparison(String body) {
        return SimpleRequest.Builder.post("/api/v1/comparisons")
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

    private static void assertValid(ValidationReport report) {
        assertTrue(report.getMessages().isEmpty(), report::toString);
    }

    private String fixture(String name) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/" + name)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
