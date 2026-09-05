package io.github.temporalrift.actionevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Contract invariant for {@code CardPlayedPayload}: exactly one of {@code targetEventId} / {@code targetPlayerId}
 * is present. The AsyncAPI schema encodes this with a sibling {@code oneOf}; these examples make the same rule
 * executable, mirroring {@code timeline-event}'s {@code EraResolutionCompletedContractTest}.
 */
class CardPlayedTargetingContractTest {

    @Test
    void asyncApiModelsExactlyOneOfTargetEventIdOrTargetPlayerId() throws IOException {
        var specification = String.join("\n", Files.readAllLines(Path.of("src/main/resources/asyncapi/asyncapi.yml")));

        assertTrue(specification.contains("oneOf:"));
        assertTrue(specification.contains("required: [ targetEventId ]"));
        assertTrue(specification.contains("required: [ targetPlayerId ]"));
        assertTrue(specification.contains("not:\n            required: [ targetPlayerId ]"));
        assertTrue(specification.contains("not:\n            required: [ targetEventId ]"));
    }

    @Test
    void acceptsAnEventTargetingPlay() {
        assertTrue(isValid(new CardPlayed(UUID.randomUUID(), null)));
    }

    @Test
    void acceptsAPlayerTargetingPlay() {
        assertTrue(isValid(new CardPlayed(null, UUID.randomUUID())));
    }

    @Test
    void rejectsAPlayWithNeitherTarget() {
        assertFalse(isValid(new CardPlayed(null, null)));
    }

    @Test
    void rejectsAPlayWithBothTargets() {
        assertFalse(isValid(new CardPlayed(UUID.randomUUID(), UUID.randomUUID())));
    }

    private static boolean isValid(CardPlayed cardPlayed) {
        return (cardPlayed.targetEventId() == null) != (cardPlayed.targetPlayerId() == null);
    }

    private record CardPlayed(UUID targetEventId, UUID targetPlayerId) {}
}
