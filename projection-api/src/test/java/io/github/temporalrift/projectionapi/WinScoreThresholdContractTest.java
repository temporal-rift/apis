package io.github.temporalrift.projectionapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WinScoreThresholdContractTest {

    @Test
    void stateResponseRequiresTheScoreVictoryThreshold() throws IOException {
        var specification = specification();

        assertTrue(specification.contains(
                "required: [ gameId, eraNumber, phase, myHand, myScore, myRevealedIntel, activeEvents, players, winScoreThreshold ]"));

        var threshold = specification.substring(
                specification.indexOf("        winScoreThreshold:"),
                specification.indexOf("        winScoreThreshold:") + 1200);
        assertTrue(threshold.contains("type: integer"));
        assertTrue(threshold.contains("minimum: 1"));
    }

    @Test
    void thresholdIsNotConfusedWithObjectiveProgress() throws IOException {
        var specification = specification();

        var threshold = specification.substring(specification.indexOf("        winScoreThreshold:"));
        assertFalse(threshold.substring(0, 1200).contains("progressCount"));
        assertTrue(specification.contains("    ObjectiveProgress:\n"));
    }

    private static String specification() throws IOException {
        return String.join(
                "\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/projection.yml")));
    }
}
