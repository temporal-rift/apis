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
    void openApiModelsFourExclusiveTargetModes() throws IOException {
        var specification =
                String.join("\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/action.yml")));

        assertTrue(specification.contains("required: [ targetEventId ]"));
        assertTrue(specification.contains("required: [ targetEventIds ]"));
        assertTrue(specification.contains("required: [ targetPlayerId ]"));
        assertTrue(specification.contains("required: [ targetPlayerIds ]"));
        assertTrue(specification.contains("minItems: 1"));
        assertTrue(specification.contains("maxItems: 3"));
        assertTrue(specification.contains("maxItems: 2"));
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
    void targetPlayerIdsIsNotUniqueItemsSoJacksonCannotSilentlyDropDuplicates() throws IOException {
        var specification =
                String.join("\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/action.yml")));

        var targetPlayerIdsBlock = specification.substring(
                specification.indexOf("targetPlayerIds:\n              type: array"),
                specification.indexOf("          oneOf:"));
        assertFalse(
                targetPlayerIdsBlock.contains("uniqueItems"),
                "targetPlayerIds must stay a plain array without uniqueItems: true. OpenAPI Generator maps a "
                        + "uniqueItems array to Set<UUID>, and Jackson silently collapses a duplicate-id JSON "
                        + "array into that Set before any validation runs — hiding exactly the duplicate "
                        + "submissions this field needs game-service to reject. Distinctness must stay a "
                        + "game-service domain concern operating on a List<UUID> that still contains the "
                        + "duplicates the client actually sent.");
    }

    @Test
    void targetPlayerIdsIsNullableSoOmittingItPassesGeneratedBeanValidation() throws IOException {
        var specification =
                String.join("\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/action.yml")));

        var targetPlayerIdsBlock =
                specification.substring(specification.indexOf("targetPlayerIds:\n              type: array"));
        assertTrue(
                targetPlayerIdsBlock.startsWith("targetPlayerIds:\n              type: array\n              nullable: true"),
                "targetPlayerIds must stay nullable: true, otherwise the generated Spring model eagerly "
                        + "initializes it to an empty Set and the generated @Size(min = 1) rejects every "
                        + "non-NULLIFY request that omits the field");
    }

    @Test
    void acceptsScalarEventListAndPlayerTargetingRequests() {
        assertTrue(isValid(new CardActionRequest(UUID.randomUUID(), null, null, null, null, null)));
        assertTrue(isValid(new CardActionRequest(
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), null, null)));
        assertTrue(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID(), UUID.randomUUID()), null, null, null, null)));
        assertTrue(isValid(new CardActionRequest(null, null, null, null, UUID.randomUUID(), null)));
        assertTrue(isValid(new CardActionRequest(null, null, null, null, null, List.of(UUID.randomUUID()))));
        assertTrue(isValid(new CardActionRequest(
                null, null, null, null, null, List.of(UUID.randomUUID(), UUID.randomUUID()))));
    }

    @Test
    void rejectsMissingMixedAndPlayerOutcomeTargets() {
        assertFalse(isValid(new CardActionRequest(null, null, null, null, null, null)));
        assertFalse(isValid(new CardActionRequest(
                UUID.randomUUID(), null, null, null, UUID.randomUUID(), null)));
        assertFalse(isValid(new CardActionRequest(
                null, null, UUID.randomUUID(), null, UUID.randomUUID(), null)));
        assertFalse(isValid(new CardActionRequest(
                null, null, null, UUID.randomUUID(), UUID.randomUUID(), null)));
        assertFalse(isValid(new CardActionRequest(
                UUID.randomUUID(), null, null, null, null, List.of(UUID.randomUUID(), UUID.randomUUID()))));
        assertFalse(isValid(new CardActionRequest(
                null, null, UUID.randomUUID(), null, null, List.of(UUID.randomUUID()))));
        assertFalse(isValid(new CardActionRequest(
                null, null, null, UUID.randomUUID(), null, List.of(UUID.randomUUID()))));
        assertFalse(isValid(new CardActionRequest(
                null, null, null, null, UUID.randomUUID(), List.of(UUID.randomUUID()))));
        assertFalse(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID()), null, null, null, List.of(UUID.randomUUID()))));
        assertFalse(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID()), null, null, null, List.of(UUID.randomUUID()))));
        assertFalse(isValid(new CardActionRequest(
                null, null, null, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()))));
    }

    @Test
    void rejectsInvalidListMode() {
        var duplicateId = UUID.randomUUID();

        assertFalse(isValid(new CardActionRequest(null, List.of(), null, null, null, null)));
        assertFalse(isValid(new CardActionRequest(null, List.of(duplicateId, duplicateId), null, null, null, null)));
        assertFalse(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), null, null,
                null, null)));
        assertFalse(isValid(new CardActionRequest(
                UUID.randomUUID(), List.of(UUID.randomUUID()), null, null, null, null)));
        assertFalse(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID()), UUID.randomUUID(), null, null, null)));
        assertFalse(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID()), null, null, UUID.randomUUID(), null)));
        assertFalse(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID()), null, null, null, List.of(UUID.randomUUID()))));
    }

    @Test
    void rejectsInvalidPlayerListMode() {
        var duplicateId = UUID.randomUUID();

        assertFalse(isValid(new CardActionRequest(null, null, null, null, null, List.of())));
        assertFalse(
                isValid(new CardActionRequest(null, null, null, null, null, List.of(duplicateId, duplicateId))));
        assertFalse(isValid(new CardActionRequest(
                null, null, null, null, null,
                List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))));
        assertFalse(isValid(new CardActionRequest(
                UUID.randomUUID(), null, null, null, null, List.of(UUID.randomUUID()))));
        assertFalse(isValid(new CardActionRequest(
                null, List.of(UUID.randomUUID()), null, null, null, List.of(UUID.randomUUID()))));
        assertFalse(isValid(new CardActionRequest(
                null, null, UUID.randomUUID(), null, null, List.of(UUID.randomUUID()))));
        assertFalse(isValid(new CardActionRequest(
                null, null, null, null, UUID.randomUUID(), List.of(UUID.randomUUID()))));
        assertFalse(isValid(new CardActionRequest(
                null, null, null, UUID.randomUUID(), null, List.of(UUID.randomUUID()))));
    }

    private static boolean isValid(CardActionRequest request) {
        var hasEvent = request.targetEventId() != null;
        var hasList = request.targetEventIds() != null;
        var hasPlayer = request.targetPlayerId() != null;
        var hasPlayerList = request.targetPlayerIds() != null;
        if (hasList) {
            var ids = request.targetEventIds();
            return ids.size() >= 1
                    && ids.size() <= 3
                    && ids.stream().distinct().count() == ids.size()
                    && !hasEvent
                    && !hasPlayer
                    && !hasPlayerList
                    && request.sourceOutcomeId() == null
                    && request.targetOutcomeId() == null;
        }
        if (hasPlayerList) {
            var ids = request.targetPlayerIds();
            return ids.size() >= 1
                    && ids.size() <= 2
                    && ids.stream().distinct().count() == ids.size()
                    && !hasEvent
                    && !hasList
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
            UUID targetPlayerId,
            List<UUID> targetPlayerIds) {}
}
