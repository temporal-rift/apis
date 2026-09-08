package io.github.temporalrift.actionevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CardPlayedTargetingContractTest {

    @Test
    void asyncApiModelsThreeExclusiveTargetModes() throws IOException {
        var specification = String.join("\n", Files.readAllLines(Path.of("src/main/resources/asyncapi/asyncapi.yml")));

        assertTrue(specification.contains("required: [ targetEventId ]"));
        assertTrue(specification.contains("required: [ targetEventIds ]"));
        assertTrue(specification.contains("required: [ targetPlayerId ]"));
        assertTrue(specification.contains("minItems: 1"));
        assertTrue(specification.contains("maxItems: 3"));
        assertTrue(specification.contains("uniqueItems: true"));
    }

    @Test
    void acceptsScalarEventListAndPlayerTargetingPlays() {
        assertTrue(isValid(new CardPlayed(UUID.randomUUID(), null, null, null, null)));
        assertTrue(isValid(new CardPlayed(
                UUID.randomUUID(), null, null, UUID.randomUUID(), UUID.randomUUID())));
        assertTrue(
                isValid(new CardPlayed(null, List.of(UUID.randomUUID(), UUID.randomUUID()), null, null, null)));
        assertTrue(isValid(new CardPlayed(null, null, UUID.randomUUID(), null, null)));
    }

    @Test
    void rejectsMissingMixedAndPlayerOutcomeTargets() {
        assertFalse(isValid(new CardPlayed(null, null, null, null, null)));
        assertFalse(isValid(new CardPlayed(UUID.randomUUID(), null, UUID.randomUUID(), null, null)));
        assertFalse(isValid(new CardPlayed(null, null, UUID.randomUUID(), UUID.randomUUID(), null)));
        assertFalse(isValid(new CardPlayed(null, null, UUID.randomUUID(), null, UUID.randomUUID())));
    }

    @Test
    void rejectsInvalidListMode() {
        var duplicateId = UUID.randomUUID();

        assertFalse(isValid(new CardPlayed(null, List.of(), null, null, null)));
        assertFalse(isValid(new CardPlayed(null, List.of(duplicateId, duplicateId), null, null, null)));
        assertFalse(isValid(new CardPlayed(
                null, List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), null, null, null)));
        assertFalse(isValid(new CardPlayed(
                UUID.randomUUID(), List.of(UUID.randomUUID()), null, null, null)));
        assertFalse(isValid(new CardPlayed(
                null, List.of(UUID.randomUUID()), UUID.randomUUID(), null, null)));
        assertFalse(isValid(new CardPlayed(
                null, List.of(UUID.randomUUID()), null, UUID.randomUUID(), null)));
        assertFalse(isValid(new CardPlayed(
                null, List.of(UUID.randomUUID()), null, null, UUID.randomUUID())));
    }

    private static boolean isValid(CardPlayed cardPlayed) {
        var hasEvent = cardPlayed.targetEventId() != null;
        var hasList = cardPlayed.targetEventIds() != null;
        var hasPlayer = cardPlayed.targetPlayerId() != null;
        if (hasList) {
            var ids = cardPlayed.targetEventIds();
            return ids.size() >= 1
                    && ids.size() <= 3
                    && ids.stream().distinct().count() == ids.size()
                    && !hasEvent
                    && !hasPlayer
                    && cardPlayed.sourceOutcomeId() == null
                    && cardPlayed.targetOutcomeId() == null;
        }
        return hasEvent != hasPlayer
                && (!hasPlayer
                        || (cardPlayed.sourceOutcomeId() == null && cardPlayed.targetOutcomeId() == null));
    }

    private record CardPlayed(
            UUID targetEventId,
            List<UUID> targetEventIds,
            UUID targetPlayerId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId) {}
}
