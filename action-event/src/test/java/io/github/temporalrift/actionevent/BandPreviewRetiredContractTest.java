package io.github.temporalrift.actionevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BandPreviewRetiredContractTest {

    private static final Path SPECIFICATION = Path.of("src/main/resources/asyncapi/asyncapi.yml");

    @Test
    void declaresNoBandPreviewMessage() throws IOException {
        var specification = specification();

        assertFalse(
                specification.contains("BandedProbabilityPublished"),
                "action-event must not reference the retired band preview message");
        assertFalse(
                specification.contains("bandedProbabilityPublished"),
                "action-event must not reference the retired band preview channel or operation");
        assertFalse(
                specification.contains("ProbabilityBand"),
                "action-event must not retain band schemas left over from the preview");
    }

    @Test
    void stillDeclaresTheRemainingActionMessages() throws IOException {
        var specification = specification();

        assertTrue(specification.contains("name: RoundSummaryPublished"));
        assertTrue(specification.contains("name: ActionRoundClosed"));
        assertTrue(specification.contains("  publishRoundSummaryPublished:"));
    }

    private static String specification() throws IOException {
        return String.join("\n", Files.readAllLines(SPECIFICATION));
    }
}
