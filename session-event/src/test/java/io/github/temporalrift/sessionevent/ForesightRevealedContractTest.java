package io.github.temporalrift.sessionevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ForesightRevealedContractTest {

    private static final Path SPECIFICATION = Path.of("src/main/resources/asyncapi/asyncapi.yml");

    @Test
    void publishesForesightRevealedAsAClosedPrivateViewerPayload() throws IOException {
        var specification = specification();
        var schema = section(specification, "    ForesightRevealedPayload:", "    ForesightRevealedEvent:");

        assertTrue(specification.contains(
                "      foresightRevealed:\n        $ref: '#/components/messages/ForesightRevealed'"));
        assertTrue(specification.contains("  publishForesightRevealed:"));
        assertTrue(specification.contains("Private per-player reveal addressed to the foretelling player"));
        assertTrue(schema.contains("additionalProperties: false"));
        assertTrue(schema.contains("required: [ gameId, eraNumber, playerId, nextEraNumber, revealedEvents ]"));
        assertTrue(schema.contains("The foretelling player and sole viewer of this private reveal."));
        assertTrue(schema.contains("nextEraNumber"));
        assertTrue(schema.contains("revealedEvents"));
        assertTrue(specification.contains("catalogEventId"));
        assertTrue(specification.contains("catalogOutcomeId"));
        assertFalse(schema.contains("actor"));
        assertFalse(schema.contains("sourcePlayerId"));
        assertFalse(schema.contains("probability"));
        assertFalse(schema.contains("band"));
        assertFalse(schema.contains("cardType"));
        assertFalse(schema.contains("grade"));
        assertFalse(schema.contains("deckSize"));
        assertFalse(schema.contains("deckState"));
    }

    @Test
    void finalEraEmptyPreviewWithAnExplicitReasonStaysValid() throws IOException {
        var specification = specification();
        var schema = section(specification, "    ForesightRevealedPayload:", "    ForesightRevealedEvent:");
        var eventSchema = section(
                specification, "    ForesightRevealedEvent:", "    ForesightRevealedOutcome:");
        var outcomeSchema = section(specification, "    ForesightRevealedOutcome:", "    Faction:");

        assertTrue(schema.contains("emptyReason"));
        assertTrue(schema.contains("Set only when revealedEvents is empty"));
        assertFalse(schema.contains("minItems:"));
        assertTrue(eventSchema.contains("additionalProperties: false"));
        assertTrue(eventSchema.contains("required: [ catalogEventId, title, outcomes ]"));
        assertTrue(outcomeSchema.contains("additionalProperties: false"));
        assertTrue(outcomeSchema.contains("required: [ catalogOutcomeId, description ]"));
    }

    private static String section(String specification, String start, String end) {
        return specification.substring(specification.indexOf(start), specification.indexOf(end));
    }

    private static String specification() throws IOException {
        return String.join("\n", Files.readAllLines(SPECIFICATION));
    }
}
