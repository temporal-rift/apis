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
 * {@code targetPlayerId} is present. The OpenAPI schema encodes this with a sibling {@code oneOf},
 * mirroring {@code action-event}'s {@code CardPlayedPayload}; these examples make the same rule
 * executable, mirroring that module's {@code CardPlayedTargetingContractTest}.
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
        assertTrue(specification.contains("not:\n                required: [ targetEventId ]"));
    }

    @Test
    void acceptsAnEventTargetingPlay() {
        assertTrue(isValid(new CardActionRequest(UUID.randomUUID(), null)));
    }

    @Test
    void acceptsAPlayerTargetingPlay() {
        assertTrue(isValid(new CardActionRequest(null, UUID.randomUUID())));
    }

    @Test
    void rejectsAPlayWithNeitherTarget() {
        assertFalse(isValid(new CardActionRequest(null, null)));
    }

    @Test
    void rejectsAPlayWithBothTargets() {
        assertFalse(isValid(new CardActionRequest(UUID.randomUUID(), UUID.randomUUID())));
    }

    private static boolean isValid(CardActionRequest request) {
        return (request.targetEventId() == null) != (request.targetPlayerId() == null);
    }

    private record CardActionRequest(UUID targetEventId, UUID targetPlayerId) {}
}
