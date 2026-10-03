package io.github.temporalrift.actionapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DeclarationDeclineContractTest {
    @Test
    void declineUsesAuthenticatedIdentityWithoutClientSuppliedPlayerOrRequestBody() throws IOException {
        var specification = Files.readString(Path.of("src/main/resources/openapi/v1/action.yml")).replace("\r\n", "\n");
        var operation = specification.substring(specification.indexOf("  /api/v1/games/{gameId}/eras/{eraNumber}/declarations/decline:"),
                specification.indexOf("  /api/v1/games/{gameId}/eras/{eraNumber}/hand-selection:"));
        assertTrue(specification.contains("security:\n  - bearerAuth:"));
        assertTrue(operation.contains("operationId: declineDeclaration"));
        assertTrue(operation.contains("'202':"));
        assertTrue(operation.contains("'409':"));
        assertFalse(operation.contains("requestBody:"));
        assertFalse(operation.contains("name: playerId"));
        assertTrue(operation.contains("including after phase closure"));
        assertTrue(specification.contains("enum: [ DECLINED ]"));
    }
}
