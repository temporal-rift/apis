package io.github.temporalrift.sessionapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LobbyResponseContractTest {

    @Test
    void lobbyResponseIdentifiesOnlyTheAuthorizedCaller() throws IOException {
        var specification = String.join(
                "\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/session.yml")));

        assertTrue(specification.contains("LobbyResponse:"));
        assertTrue(specification.contains("currentPlayerId:"));
        assertTrue(specification.contains("The authenticated member whose authorized request produced this response."));
        assertTrue(specification.contains("never included in a non-member denial"));
        assertTrue(specification.contains("code: 403-01"));
    }
}
