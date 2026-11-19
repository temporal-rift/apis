package io.github.temporalrift.timelineevent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ChainLinkInvalidatedContractTest {

    @Test
    void everyInvalidationNamesOneOfTheFiveCauses() throws IOException {
        var specification = Files.readString(Path.of("src/main/resources/asyncapi/asyncapi.yml"));
        var start = specification.indexOf("    ChainLinkInvalidatedPayload:");
        var payload = specification.substring(start, specification.indexOf("    ThreadRejectedPayload:", start));

        assertTrue(payload.contains("cause:"));
        for (var cause : new String[] {
            "RESOLVED_DIFFERENTLY", "NO_WINNER", "CHAIN_CONFLICT_CLEARED", "ANNIHILATE_ABSORBED", "GAME_ENDED"
        }) {
            assertTrue(payload.contains("- " + cause), cause);
        }
        assertTrue(payload.substring(payload.indexOf("      required:")).contains("chainLength, cause ]"));
    }
}
