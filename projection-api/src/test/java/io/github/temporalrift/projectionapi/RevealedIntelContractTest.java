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
                "required: [ gameId, eraNumber, phase, myHand, myScore, myRevealedIntel, activeEvents, players, winScoreThreshold ]"));
        var revealedIntelSchema = specification.substring(specification.indexOf("        myRevealedIntel:"));
        assertFalse(revealedIntelSchema.startsWith("        myRevealedIntel:\n          type: array\n          nullable: true"));
    }

    @Test
    void tracedInfluenceIntelCarriesMimicAttribution() throws IOException {
        var specification = String.join(
                "\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/projection.yml")));

        var intelSchema = specification.substring(
                specification.indexOf("    RevealedIntel:"),
                specification.indexOf("    RevealedProbabilityOutcome:"));
        assertTrue(intelSchema.contains("        influencerPlayerIds:"));
        assertTrue(intelSchema.contains("        mimicInfluencerPlayerIds:\n          type: array"));
        assertTrue(intelSchema.contains("uniqueItems: true"));
        assertFalse(intelSchema.contains("minItems:"));
    }
}
