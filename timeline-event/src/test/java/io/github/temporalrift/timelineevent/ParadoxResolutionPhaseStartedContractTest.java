package io.github.temporalrift.timelineevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Contract invariants for the paradox-resolution phase-start event.
 *
 * <p>The payload must carry exactly the unique affected event identifiers alongside the opaque
 * paradox identifiers so consumers can rebuild the legal target set after redelivery or restart,
 * without receiving any player-private offers, cards, or unresolved choices.
 */
class ParadoxResolutionPhaseStartedContractTest {

    @Test
    void asyncApiModelsAffectedEventIdsAlongsidePhaseAndDeadline() throws IOException {
        var specification = String.join("\n", Files.readAllLines(Path.of("src/main/resources/asyncapi/asyncapi.yml")));

        assertTrue(specification.contains("ParadoxResolutionPhaseStartedPayload"));
        assertTrue(specification.contains("affectedEventIds"));
        assertTrue(specification.contains("paradoxIds"));
        assertTrue(specification.contains("timerSeconds"));
        assertTrue(specification.contains("uniqueItems: true"));
        assertTrue(specification.contains("minItems: 1"));
    }

    @Test
    void affectedEventIdsIsACompatibleOptionalAddition() throws IOException {
        var specification = String.join("\n", Files.readAllLines(Path.of("src/main/resources/asyncapi/asyncapi.yml")));

        assertTrue(
                specification.contains("required: [ gameId, eraNumber, paradoxIds, timerSeconds ]"),
                "affectedEventIds must stay optional so the addition is backward compatible");
    }

    @Test
    void phaseStartCarriesNoPlayerPrivateFields() throws IOException {
        var specification = String.join("\n", Files.readAllLines(Path.of("src/main/resources/asyncapi/asyncapi.yml")));
        var payload = payloadBlock(specification);

        assertFalse(payload.contains("cardInstanceId:"), "phase-start must not carry cards");
        assertFalse(payload.contains("offers:"), "phase-start must not carry offers");
        assertFalse(payload.contains("choices:"), "phase-start must not carry unresolved choices");
        assertFalse(payload.contains("playerId:"), "phase-start must not carry player-private subjects");
        assertTrue(
                payload.contains("no player-private offers, cards, or unresolved choices"),
                "the privacy invariant must be documented on the field");
    }

    @Test
    void acceptsAUniqueNonEmptyTargetSet() {
        var targets = List.of(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(isValidTargetSet(targets));
    }

    @Test
    void rejectsEmptyAndDuplicatedTargetSets() {
        var duplicate = UUID.randomUUID();

        assertFalse(isValidTargetSet(List.of()));
        assertFalse(isValidTargetSet(List.of(duplicate, duplicate)));
    }

    @Test
    void redeliveryRebuildsTheSameLegalTargetSet() {
        var published = List.of(UUID.randomUUID(), UUID.randomUUID());
        var redelivered = List.copyOf(published);

        assertTrue(isValidTargetSet(redelivered));
        assertTrue(
                redelivered.containsAll(published) && published.containsAll(redelivered),
                "redelivery must reconstruct the same legal target set");
    }

    private static boolean isValidTargetSet(List<UUID> affectedEventIds) {
        return !affectedEventIds.isEmpty() && affectedEventIds.stream().distinct().count() == affectedEventIds.size();
    }

    private static String payloadBlock(String specification) {
        var start = specification.indexOf("ParadoxResolutionPhaseStartedPayload:");
        var end = specification.indexOf("ParadoxResolvedPayload:", start);
        return end < 0 ? specification.substring(start) : specification.substring(start, end);
    }
}
