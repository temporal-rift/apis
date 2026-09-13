package io.github.temporalrift.chainsapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ChainsContractTest {

    @Test
    void openApiModelsGatedChainRead() throws IOException {
        var specification =
                String.join("\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/chains.yml")));

        assertTrue(specification.contains("/api/v1/games/{gameId}/chains"));
        assertTrue(specification.contains("operationId: getChain"));
        assertTrue(specification.contains("required: [ chainId, status, chainLength, links ]"));
        assertTrue(specification.contains("required: [ eventId, outcomeId, eraNumber ]"));
        assertTrue(specification.contains("- ACTIVE"));
        assertTrue(specification.contains("- COMPLETED"));
        assertTrue(specification.contains("- BROKEN"));
        assertTrue(specification.contains("code: 403-01"));
        assertTrue(specification.contains("code: 404-01"));
        assertTrue(specification.contains("code: 404-02"));
    }
}
