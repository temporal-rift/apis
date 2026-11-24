package io.github.temporalrift.projectionapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ChainConfirmedAndCascadedContractTest {

    @Test
    void chainStateCarriesOptionalConfirmedEventIds() throws IOException {
        var chainState = block("    ChainState:\n", "    PendingChainLink:\n");

        assertTrue(chainState.contains("required: [ status, length ]"), "ChainState keeps status+length as its only required fields");
        assertFalse(chainState.contains("confirmedEventIds ]"), "confirmedEventIds must stay optional");
        assertTrue(chainState.contains("confirmedEventIds:"), "ChainState must carry confirmedEventIds");
        assertTrue(chainState.contains("uniqueItems: true"), "confirmedEventIds must not repeat an event");
        assertTrue(chainState.contains("format: uuid"), "confirmedEventIds entries must be uuids");
        assertTrue(chainState.contains("in link order"), "confirmedEventIds must document link order");
    }

    @Test
    void playerGameStateCarriesOptionalCascadedEventIds() throws IOException {
        var specification = specification();

        assertTrue(
                specification.contains("required: [ gameId, eraNumber, phase, myHand, myScore, myRevealedIntel, activeEvents, players, winScoreThreshold ]"),
                "PlayerGameStateResponse required list stays unchanged");
        assertTrue(specification.contains("cascadedEventIds:"), "PlayerGameStateResponse must carry cascadedEventIds");

        var response = specification.substring(specification.indexOf("    PlayerGameStateResponse:"));
        var cascaded = response.substring(response.indexOf("cascadedEventIds:"));
        assertTrue(cascaded.contains("uniqueItems: true"), "cascadedEventIds must not repeat an event");
        assertTrue(cascaded.contains("format: uuid"), "cascadedEventIds entries must be uuids");
        assertTrue(cascaded.contains("first cascaded"), "cascadedEventIds must document first-cascade order");
    }

    private static String block(String start, String end) throws IOException {
        var specification = specification();
        var from = specification.indexOf(start);
        return specification.substring(from, specification.indexOf(end, from));
    }

    private static String specification() throws IOException {
        return String.join(
                "\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/projection.yml")));
    }
}
