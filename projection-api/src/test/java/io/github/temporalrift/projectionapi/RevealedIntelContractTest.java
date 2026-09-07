package io.github.temporalrift.projectionapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RevealedIntelContractTest {

    @Test
    void stateResponseRequiresNonNullRevealedIntel() throws IOException {
        var specification = String.join(
                "\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/projection.yml")));

        assertTrue(specification.contains(
                "required: [ gameId, eraNumber, phase, myHand, myScore, myRevealedIntel, activeEvents, players ]"));
        var revealedIntelSchema = specification.substring(specification.indexOf("        myRevealedIntel:"));
        assertFalse(revealedIntelSchema.startsWith("        myRevealedIntel:\n          type: array\n          nullable: true"));
        assertTrue(revealedIntelSchema.contains("Always present and\n            empty when none"));
    }
}
