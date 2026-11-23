package io.github.temporalrift.projectionapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PendingChainLinkContractTest {

    @Test
    void theChainStateCarriesAnOptionalPendingLink() throws IOException {
        var chainState = block("    ChainState:\n", "    PendingChainLink:\n");

        assertTrue(chainState.contains("pendingLink:"));
        assertTrue(chainState.contains("$ref: '#/components/schemas/PendingChainLink'"));
        assertTrue(chainState.contains("required: [ status, length ]"));
        assertFalse(chainState.contains("required: [ status, length, pendingLink ]"));
    }

    @Test
    void aPendingLinkNamesItsEventAndOutcome() throws IOException {
        var link = block("    PendingChainLink:\n", "    Deadlines:\n");

        assertTrue(link.contains("required: [ eventId, outcomeId ]"));
        assertTrue(link.contains("eventId: { type: string, format: uuid }"));
        assertTrue(link.contains("outcomeId: { type: string, format: uuid }"));
    }

    private static String block(String start, String end) throws IOException {
        var specification = String.join(
                "\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/projection.yml")));
        var from = specification.indexOf(start);
        return specification.substring(from, specification.indexOf(end, from));
    }
}
