package io.github.temporalrift.projectionapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ForesightPreviewContractTest {

    private static String specification() throws IOException {
        return String.join(
                "\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/projection.yml")));
    }

    @Test
    void stateResponseCarriesNullableForesightPreview() throws IOException {
        var specification = specification();

        assertTrue(specification.contains("        myForesightPreview:"));
        var fieldSchema = specification.substring(specification.indexOf("        myForesightPreview:"));
        assertTrue(fieldSchema.contains("nullable: true"));
        assertTrue(fieldSchema.contains("nextEraNumber"));
        assertTrue(fieldSchema.contains("revealedEvents"));
        assertTrue(fieldSchema.contains("ForesightPreviewEvent"));
        assertTrue(fieldSchema.contains("catalogEventId"));
        assertTrue(fieldSchema.contains("catalogOutcomeId"));
        assertTrue(fieldSchema.contains("emptyReason"));
        assertTrue(fieldSchema.contains("ForesightPreviewOutcome"));
    }

    @Test
    void foresightPreviewDescribesNullAndEraBoundaryExpiry() throws IOException {
        var specification = specification();

        var fieldSchema = specification.substring(
                specification.indexOf("        myForesightPreview:"),
                specification.indexOf("        activeEvents:"));
        assertTrue(fieldSchema.contains("Null when the caller has no Foresight preview"));
        assertTrue(fieldSchema.contains("current era"));
        assertTrue(fieldSchema.contains("expires at the"));
        assertTrue(fieldSchema.contains("era boundary"));
    }

    @Test
    void foresightPreviewCarriesNoForbiddenContent() throws IOException {
        var specification = specification();

        var fieldSchema = specification.substring(
                specification.indexOf("        myForesightPreview:"),
                specification.indexOf("        activeEvents:"));
        var eventSchema = specification.substring(
                specification.indexOf("    ForesightPreviewEvent:"),
                specification.indexOf("    CardGrade:"));
        var previewSchemas = fieldSchema + eventSchema;
        assertFalse(previewSchemas.contains("playerId"));
        assertFalse(previewSchemas.contains("gameId"));
        assertFalse(previewSchemas.contains("probability"));
        assertFalse(previewSchemas.contains("band"));
        assertFalse(previewSchemas.toLowerCase().contains("deck state"));
    }
}
