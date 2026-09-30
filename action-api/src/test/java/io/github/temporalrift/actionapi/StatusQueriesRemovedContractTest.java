package io.github.temporalrift.actionapi;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class StatusQueriesRemovedContractTest {

    @Test
    void doesNotServeRoundOrParadoxResolutionStatus() throws IOException {
        var specification = readSpecification();

        assertFalse(specification.contains("/rounds/{roundNumber}/status:"));
        assertFalse(specification.contains("/paradox-resolution/status:"));
        assertFalse(specification.contains("operationId: getRoundStatus"));
        assertFalse(specification.contains("operationId: getParadoxResolutionStatus"));
    }

    @Test
    void dropsTheStatusResponseSchemas() throws IOException {
        var specification = readSpecification();

        for (var schema : new String[] {
            "RoundStatusResponse:",
            "MyRoundSubmission:",
            "    RoundStatus:",
            "ParadoxResolutionStatusResponse:",
            "EligibleResolutionCard:",
            "    CardType:",
            "    CardGrade:"
        }) {
            assertFalse(specification.contains(schema), schema);
        }
        assertFalse(specification.contains("mySubmitted"));
        assertFalse(specification.contains("mySubmission"));
    }

    private static String readSpecification() throws IOException {
        return String.join("\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/action.yml")));
    }
}
