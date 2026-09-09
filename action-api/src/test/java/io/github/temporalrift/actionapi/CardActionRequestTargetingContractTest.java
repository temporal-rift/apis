package io.github.temporalrift.actionapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CardActionRequestTargetingContractTest {

    @Test
    void openApiModelsThreeExclusiveTargetModes() throws IOException {
        var specification =
                String.join("\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/action.yml")));

        assertTrue(specification.contains("required: [ targetEventId ]"));
        assertTrue(specification.contains("required: [ targetEventIds ]"));
        assertTrue(specification.contains("required: [ targetPlayerId ]"));
        assertTrue(specification.contains("minItems: 1"));
        assertTrue(specification.contains("maxItems: 3"));
    }

    @Test
    void targetEventIdsIsNotUniqueItemsSoJacksonCannotSilentlyDropDuplicates() throws IOException {
        var specification =
                String.join("\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/action.yml")));

        var targetEventIdsBlock = specification.substring(
                specification.indexOf("targetEventIds:\n              type: array"),
                specification.indexOf("sourceOutcomeId:"));
        assertFalse(
                targetEventIdsBlock.contains("uniqueItems"),
                "targetEventIds must stay a plain array without uniqueItems: true. OpenAPI Generator maps a "
                        + "uniqueItems array to Set<UUID>, and Jackson silently collapses a duplicate-id JSON "
                        + "array into that Set before any validation runs — hiding exactly the duplicate "
                        + "submissions this field needs game-service to reject. Distinctness must stay a "
                        + "game-service domain concern operating on a List<UUID> that still contains the "
                        + "duplicates the client actually sent.");
    }

    @Test
    void targetEventIdsIsNullableSoOmittingItPassesGeneratedBeanValidation() throws IOException {
        var specification =
                String.join("\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/action.yml")));

        var targetEventIdsBlock =
                specification.substring(specification.indexOf("targetEventIds:\n              type: array"));
        assertTrue(
                targetEventIdsBlock.startsWith("targetEventIds:\n              type: array\n              nullable: true"),
                "targetEventIds must stay nullable: true, otherwise the generated Spring model eagerly "
                        + "initializes it to an empty Set and the generated @Size(min = 1) rejects every "
                        + "non-SCAN request that omits the field");
    }

    @Test
    void acceptsScalarEventListAndPlayerTargetingRequests() {
        assertTrue(isValid(new CardActionRequest(UUID.randomUUID(), null, null, null, null)));
        assertTrue(isValid(new CardActionRequest(
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), null)));
        assertTrue(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID(), UUID.randomUUID()), null, null, null)));
        assertTrue(isValid(new CardActionRequest(null, null, null, null, UUID.randomUUID())));
    }

    @Test
    void rejectsMissingMixedAndPlayerOutcomeTargets() {
        assertFalse(isValid(new CardActionRequest(null, null, null, null, null)));
        assertFalse(isValid(new CardActionRequest(
                UUID.randomUUID(), null, null, null, UUID.randomUUID())));
        assertFalse(isValid(new CardActionRequest(
                null, null, UUID.randomUUID(), null, UUID.randomUUID())));
        assertFalse(isValid(new CardActionRequest(
                null, null, null, UUID.randomUUID(), UUID.randomUUID())));
    }

    @Test
    void rejectsInvalidListMode() {
        var duplicateId = UUID.randomUUID();

        assertFalse(isValid(new CardActionRequest(null, List.of(), null, null, null)));
        assertFalse(isValid(new CardActionRequest(null, List.of(duplicateId, duplicateId), null, null, null)));
        assertFalse(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), null, null, null)));
        assertFalse(isValid(new CardActionRequest(
                UUID.randomUUID(), List.of(UUID.randomUUID()), null, null, null)));
        assertFalse(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID()), UUID.randomUUID(), null, null)));
        assertFalse(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID()), null, UUID.randomUUID(), null)));
        assertFalse(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID()), null, null, UUID.randomUUID())));
    }

    private static boolean isValid(CardActionRequest request) {
        var hasEvent = request.targetEventId() != null;
        var hasList = request.targetEventIds() != null;
        var hasPlayer = request.targetPlayerId() != null;
        if (hasList) {
            var ids = request.targetEventIds();
            return ids.size() >= 1
                    && ids.size() <= 3
                    && ids.stream().distinct().count() == ids.size()
                    && !hasEvent
                    && !hasPlayer
                    && request.sourceOutcomeId() == null
                    && request.targetOutcomeId() == null;
        }
        return hasEvent != hasPlayer
                && (!hasPlayer || (request.sourceOutcomeId() == null && request.targetOutcomeId() == null));
    }

    private record CardActionRequest(
            UUID targetEventId,
            List<UUID> targetEventIds,
            UUID sourceOutcomeId,
            UUID targetOutcomeId,
            UUID targetPlayerId) {}
}
