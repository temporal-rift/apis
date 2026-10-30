package io.github.temporalrift.timelineevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ParadoxCascadedContractTest {

    @Test
    void cascadeIdentifiesEveryPersistentFindingOncePerAffectedEvent() throws IOException {
        var specification = Files.readString(Path.of("src/main/resources/asyncapi/asyncapi.yml"));
        var payload = payloadBlock(specification);
        var findingIds = payload.substring(payload.indexOf("        paradoxIds:"), payload.indexOf("        affectedEventId:"));

        assertTrue(findingIds.contains("minItems: 1"));
        assertTrue(findingIds.contains("uniqueItems: true"));
        assertTrue(findingIds.contains("items: { type: string, format: uuid }"));
    }

    @Test
    void olderFactsAndConsumersRetainTheRequiredPayloadShape() throws IOException {
        var specification = Files.readString(Path.of("src/main/resources/asyncapi/asyncapi.yml"));
        var payload = payloadBlock(specification);
        var required = payload.substring(payload.indexOf("      required: ["));

        assertTrue(required.contains("gameId, eraNumber, paradoxId, affectedEventId, carryForwardProbabilityState"));
        assertFalse(required.contains("paradoxIds"));
    }

    private static String payloadBlock(String specification) {
        var start = specification.indexOf("    ParadoxCascadedPayload:");
        var end = specification.indexOf("    OutcomeAppliedProbabilityState:", start);
        return specification.substring(start, end);
    }
}
