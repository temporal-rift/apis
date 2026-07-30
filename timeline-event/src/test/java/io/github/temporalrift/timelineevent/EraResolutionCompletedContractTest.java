package io.github.temporalrift.timelineevent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Contract invariants shared by the future timeline producer and game-service consumers.
 *
 * <p>The AsyncAPI schema can enforce mutually exclusive terminal states, but cannot compare a payload with the
 * separate {@code EventsDrawn.events} list. These examples make the required cross-event validation executable.
 */
class EraResolutionCompletedContractTest {

    @Test
    void asyncApiModelsMutuallyExclusiveTerminalStatesAndTheRevealOrderSource() throws IOException {
        var specification = Files.readString(Path.of("src/main/resources/asyncapi/asyncapi.yml"));

        assertTrue(specification.contains("oneOf:"));
        assertTrue(specification.contains("const: OUTCOME_APPLIED"));
        assertTrue(specification.contains("const: CASCADED"));
        assertTrue(specification.contains("not:\n            required: [ winningOutcomeId ]"));
        assertTrue(specification.contains("EventsDrawn.events"));
        assertTrue(specification.contains("revealIndex"));
    }

    @Test
    void acceptsTerminalEntriesInTheirSourceRevealOrder() {
        var eventsDrawnOrder = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var terminalResolutions = List.of(
                TerminalResolution.outcomeApplied(eventsDrawnOrder.getFirst(), 0),
                TerminalResolution.cascaded(eventsDrawnOrder.get(1), 1),
                TerminalResolution.outcomeApplied(eventsDrawnOrder.get(2), 2));

        assertDoesNotThrow(() -> verifyAgainstEventsDrawn(eventsDrawnOrder, terminalResolutions));
    }

    @Test
    void rejectsASecondTerminalEntryForTheSameEvent() {
        var eventId = UUID.randomUUID();
        var eventsDrawnOrder = List.of(eventId, UUID.randomUUID());
        var terminalResolutions = List.of(
                TerminalResolution.cascaded(eventId, 0),
                TerminalResolution.outcomeApplied(eventId, 1));

        assertThrows(IllegalArgumentException.class, () -> verifyAgainstEventsDrawn(eventsDrawnOrder, terminalResolutions));
    }

    @Test
    void rejectsParadoxResolutionOrderWhenItDiffersFromRevealOrder() {
        var eventsDrawnOrder = List.of(UUID.randomUUID(), UUID.randomUUID());
        var terminalResolutions = List.of(
                TerminalResolution.cascaded(eventsDrawnOrder.get(1), 0),
                TerminalResolution.cascaded(eventsDrawnOrder.getFirst(), 1));

        assertThrows(IllegalArgumentException.class, () -> verifyAgainstEventsDrawn(eventsDrawnOrder, terminalResolutions));
    }

    private static void verifyAgainstEventsDrawn(
            List<UUID> eventsDrawnOrder, List<TerminalResolution> terminalResolutions) {
        if (eventsDrawnOrder.size() != terminalResolutions.size()) {
            throw new IllegalArgumentException("Every active event must have exactly one terminal resolution");
        }
        for (var index = 0; index < eventsDrawnOrder.size(); index++) {
            var resolution = terminalResolutions.get(index);
            if (resolution.revealIndex() != index || !eventsDrawnOrder.get(index).equals(resolution.eventId())) {
                throw new IllegalArgumentException("Terminal resolutions must preserve EventsDrawn.events order");
            }
            if (resolution.terminalState() == TerminalState.OUTCOME_APPLIED && resolution.winningOutcomeId() == null) {
                throw new IllegalArgumentException("OUTCOME_APPLIED requires winningOutcomeId");
            }
            if (resolution.terminalState() == TerminalState.CASCADED && resolution.winningOutcomeId() != null) {
                throw new IllegalArgumentException("CASCADED forbids winningOutcomeId");
            }
        }
    }

    private record TerminalResolution(
            UUID eventId, int revealIndex, TerminalState terminalState, UUID winningOutcomeId) {

        static TerminalResolution outcomeApplied(UUID eventId, int revealIndex) {
            return new TerminalResolution(eventId, revealIndex, TerminalState.OUTCOME_APPLIED, UUID.randomUUID());
        }

        static TerminalResolution cascaded(UUID eventId, int revealIndex) {
            return new TerminalResolution(eventId, revealIndex, TerminalState.CASCADED, null);
        }

    }

    private enum TerminalState {
        OUTCOME_APPLIED,
        CASCADED
    }
}
