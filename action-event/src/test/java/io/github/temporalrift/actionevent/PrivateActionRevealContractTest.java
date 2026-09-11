package io.github.temporalrift.actionevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PrivateActionRevealContractTest {

    private static final Path SPECIFICATION = Path.of("src/main/resources/asyncapi/asyncapi.yml");

    @Test
    void publishesPlayerJammedAsAClosedPrivateViewerPayload() throws IOException {
        var specification = specification();
        var schema = section(specification, "    PlayerJammedPayload:", "    InfluenceTracedPayload:");

        assertTrue(specification.contains("      playerJammed:\n        $ref: '#/components/messages/PlayerJammed'"));
        assertTrue(specification.contains("  publishPlayerJammed:"));
        assertTrue(specification.contains("Private per-player reveal addressed to the suppressed player"));
        assertTrue(schema.contains("additionalProperties: false"));
        assertTrue(schema.contains("required: [ gameId, eraNumber, playerId, jammedUntilRound ]"));
        assertFalse(schema.contains("actor"));
        assertFalse(schema.contains("sourcePlayerId"));
        assertFalse(schema.contains("jammerPlayerId"));
    }

    @Test
    void publishesInfluenceTracedWithoutActionDetailsAndAllowsNoInfluencers() throws IOException {
        var specification = specification();
        var schema = section(specification, "    InfluenceTracedPayload:", "    HandCardInterceptedPayload:");

        assertTrue(specification.contains("      influenceTraced:\n        $ref: '#/components/messages/InfluenceTraced'"));
        assertTrue(specification.contains("  publishInfluenceTraced:"));
        assertTrue(specification.contains("Private per-player reveal addressed to the tracing player"));
        assertTrue(schema.contains("additionalProperties: false"));
        assertTrue(schema.contains(
                "required: [ gameId, eraNumber, roundNumber, playerId, targetEventId, influencerPlayerIds ]"));
        assertTrue(schema.contains("uniqueItems: true"));
        assertFalse(schema.contains("minItems:"));
        assertFalse(schema.contains("cardType"));
        assertFalse(schema.contains("grade"));
        assertFalse(schema.contains("outcomeId"));
        assertFalse(schema.contains("magnitude"));
    }

    @Test
    void publishesHandCardInterceptedAsAClosedPrivateViewerPayload() throws IOException {
        var specification = specification();
        var schema = section(specification, "    HandCardInterceptedPayload:", "    ActionRoundTimerExpiredPayload:");

        assertTrue(specification.contains(
                "      handCardIntercepted:\n        $ref: '#/components/messages/HandCardIntercepted'"));
        assertTrue(specification.contains("  publishHandCardIntercepted:"));
        assertTrue(specification.contains("Private per-player reveal addressed to the intercepting player"));
        assertTrue(schema.contains("additionalProperties: false"));
        assertTrue(schema.contains(
                "required: [ gameId, eraNumber, roundNumber, playerId, targetPlayerId, revealedCards ]"));
        assertTrue(schema.contains("maxItems: 2"));
        assertFalse(schema.contains("minItems:"));
        assertTrue(schema.contains("required: [ cardInstanceId, cardType, grade ]"));
        assertFalse(schema.contains("actor"));
        assertFalse(schema.contains("sourcePlayerId"));
        assertFalse(schema.contains("interceptorPlayerId"));
    }

    private static String section(String specification, String start, String end) {
        return specification.substring(specification.indexOf(start), specification.indexOf(end));
    }

    private static String specification() throws IOException {
        return String.join("\n", Files.readAllLines(SPECIFICATION));
    }
}
