package io.github.temporalrift.actionevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ParadoxResolutionPassContractTest {

    private static final Path SPECIFICATION = Path.of("src/main/resources/asyncapi/asyncapi.yml");

    @Test
    void publishesTheParadoxResolutionPassOnGameEvents() throws IOException {
        var specification = specification();

        assertTrue(specification.contains(
                "      paradoxResolutionPassed:\n        $ref: '#/components/messages/ParadoxResolutionPassed'"));
        assertTrue(specification.contains("  publishParadoxResolutionPassed:"));
    }

    @Test
    void passCountsTowardTheAllSubmittedCloseAndAppliesNothing() throws IOException {
        var specification = specification();

        assertTrue(specification.contains("the player's single phase slot exactly like ParadoxResolutionCardPlayed"),
                "the pass must consume the same slot a resolution card does");
        assertTrue(specification.contains("once every player has submitted or passed"),
                "consumers must count the pass toward the all-submitted close");
        assertTrue(specification.contains("neutral skip a timer expiry produces"),
                "the pass must resolve as the neutral timer-expiry skip");
    }

    @Test
    void passPayloadIsClosedAndCarriesNoCard() throws IOException {
        var schema = section(
                specification(), "    ParadoxResolutionPassedPayload:", "    SpecialActionPlayedPayload:");

        assertTrue(schema.contains("additionalProperties: false"));
        assertTrue(schema.contains("required: [ gameId, eraNumber, playerId ]"));
        assertFalse(schema.contains("cardInstanceId"));
        assertFalse(schema.contains("cardType"));
        assertFalse(schema.contains("grade"));
        assertFalse(schema.contains("targetEventId"));
        assertFalse(schema.contains("targetOutcomeId"));
    }

    private static String specification() throws IOException {
        return String.join("\n", Files.readAllLines(SPECIFICATION));
    }

    private static String section(String specification, String start, String end) {
        var startIndex = specification.indexOf(start);
        var endIndex = specification.indexOf(end, startIndex);
        return specification.substring(startIndex, endIndex);
    }
}
