package io.github.temporalrift.actionapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Contract invariant for {@code CardActionRequest}: exactly one of {@code targetEventId} /
 * {@code targetPlayerId} is present, and a player target excludes every event-coordinate field
 * ({@code targetEventId}, {@code sourceOutcomeId}, {@code targetOutcomeId}) — an outcome only means
 * something in the context of an event, so a stray outcome id alongside a player target is malformed,
 * not harmless. The OpenAPI schema encodes this with a sibling {@code oneOf}, mirroring
 * {@code action-event}'s {@code CardPlayedPayload}; these examples make the same rule executable,
 * mirroring that module's {@code CardPlayedTargetingContractTest}.
 */
class CardActionRequestTargetingContractTest {

    @Test
    void openApiModelsExactlyOneOfTargetEventIdOrTargetPlayerId() throws IOException {
        var specification =
                String.join("\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/action.yml")));

        assertTrue(specification.contains("oneOf:"));
        assertTrue(specification.contains("required: [ targetEventId ]"));
        assertTrue(specification.contains("required: [ targetPlayerId ]"));
        assertTrue(specification.contains("not:\n                required: [ targetPlayerId ]"));
        assertTrue(specification.contains(
                "not:\n                anyOf:\n                  - required: [ targetEventId ]\n"
                        + "                  - required: [ sourceOutcomeId ]\n"
                        + "                  - required: [ targetOutcomeId ]"));
    }

    @Test
    void acceptsAnEventTargetingPlay() {
        assertTrue(isValid(new CardActionRequest(UUID.randomUUID(), null, null, null)));
    }

    @Test
    void acceptsAnEventTargetingPlayWithOutcomes() {
        assertTrue(isValid(new CardActionRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null)));
    }

    @Test
    void acceptsAPlayerTargetingPlay() {
        assertTrue(isValid(new CardActionRequest(null, null, null, UUID.randomUUID())));
    }

    @Test
    void rejectsAPlayWithNeitherTarget() {
        assertFalse(isValid(new CardActionRequest(null, null, null, null)));
    }

    @Test
    void rejectsAPlayWithBothEventAndPlayerTargets() {
        assertFalse(isValid(new CardActionRequest(UUID.randomUUID(), null, null, UUID.randomUUID())));
    }

    @Test
    void rejectsAPlayerTargetCarryingASourceOutcome() {
        assertFalse(isValid(new CardActionRequest(null, UUID.randomUUID(), null, UUID.randomUUID())));
    }

    @Test
    void rejectsAPlayerTargetCarryingATargetOutcome() {
        assertFalse(isValid(new CardActionRequest(null, null, UUID.randomUUID(), UUID.randomUUID())));
    }

    private static boolean isValid(CardActionRequest request) {
        var hasEventCoordinate = request.targetEventId() != null
                || request.sourceOutcomeId() != null
                || request.targetOutcomeId() != null;
        var hasPlayerCoordinate = request.targetPlayerId() != null;
        return hasEventCoordinate != hasPlayerCoordinate;
    }

    private record CardActionRequest(
            UUID targetEventId, UUID sourceOutcomeId, UUID targetOutcomeId, UUID targetPlayerId) {}
}
