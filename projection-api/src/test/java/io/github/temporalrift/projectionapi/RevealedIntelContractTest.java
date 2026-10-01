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
        assertTrue(revealedIntelSchema.contains("Always present and\n            empty when none"));
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
        assertTrue(intelSchema.contains("also listed in influencerPlayerIds"));
        assertTrue(intelSchema.contains("Only a Revisionist can play Mimic"));
        assertTrue(intelSchema.contains("Always present for an INFLUENCE entry"));
        assertTrue(intelSchema.contains("an\n            empty list means no Mimic influencer"));
        assertFalse(intelSchema.contains("minItems:"));
    }

    @Test
    void jamStateDescribesItsFinalPrivateRoundScopedSemantics() throws IOException {
        var specification = String.join(
                "\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/projection.yml")));

        var jamSchema = specification.substring(
                specification.indexOf("        myJammedUntilRound:"),
                specification.indexOf("        myRevealedIntel:"));
        assertTrue(jamSchema.contains("authenticated player's faction specials are blocked"));
        assertTrue(jamSchema.contains("private to that player"));
        assertTrue(jamSchema.contains("cleared after the round passes"));
        assertTrue(jamSchema.contains("never carried across an era"));
        assertFalse(jamSchema.contains("Deferred to a later slice"));
    }
}
