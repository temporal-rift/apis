package io.github.temporalrift.sessionevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GameStartedContractTest {

    private static final Path SPECIFICATION = Path.of("src/main/resources/asyncapi/asyncapi.yml");

    @Test
    void gameStartedCarriesTheScoreVictoryThreshold() throws IOException {
        var specification = specification();
        var schema = section(specification, "    GameStartedPayload:", "    GameStartedPlayer:");

        assertTrue(schema.contains("winScoreThreshold:"));
        assertTrue(schema.contains("minimum: 1"));
        assertTrue(schema.contains(
                "required: [ gameId, lobbyId, players, totalFactions, deckSize, winScoreThreshold ]"));
        assertTrue(schema.contains("qualifies for normal victory"));
        assertTrue(schema.contains("Distinct from the per-faction objective threshold"));
        assertTrue(schema.contains("never hard-code it"));
    }

    @Test
    void thresholdIsGameScopedAndSeparateFromObjectives() throws IOException {
        var specification = specification();
        var schema = section(specification, "    GameStartedPayload:", "    GameStartedPlayer:");

        assertTrue(schema.contains("identical for every player"));
        assertTrue(schema.contains("fixed for the life of the game"));
        assertFalse(schema.contains("ObjectiveProgress"));
    }

    private static String section(String specification, String start, String end) {
        return specification.substring(specification.indexOf(start), specification.indexOf(end));
    }

    private static String specification() throws IOException {
        return String.join("\n", Files.readAllLines(SPECIFICATION));
    }
}
