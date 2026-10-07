package io.github.temporalrift.simulationcontrolapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class SimulationControlContractTest {

    private static final Path SPECIFICATION =
            Path.of("src/main/resources/openapi/v1/simulation-control.yml");
    private static final String UINT64_DECIMAL_PATTERN =
            "^(?:0|[1-9][0-9]{0,18}|1[0-7][0-9]{18}|18[0-3][0-9]{17}|184[0-3][0-9]{16}|"
                    + "1844[0-5][0-9]{15}|18446[0-6][0-9]{14}|184467[0-3][0-9]{13}|"
                    + "1844674[0-3][0-9]{12}|184467440[0-6][0-9]{10}|1844674407[0-2][0-9]{9}|"
                    + "18446744073[0-6][0-9]{8}|1844674407370[0-8][0-9]{6}|"
                    + "18446744073709[0-4][0-9]{5}|184467440737095[0-4][0-9]{4}|"
                    + "18446744073709550[0-9]{3}|18446744073709551[0-5][0-9]{2}|"
                    + "1844674407370955160[0-9]|1844674407370955161[0-4]|"
                    + "18446744073709551615)$";

    @Test
    void generatedInterfacesExposeAllControlOperations() throws IOException {
        var specification = readSpecification();

        assertTrue(specification.contains("operationId: configureSimulationExecution"));
        assertTrue(specification.contains("operationId: getSimulationCheckpoint"));
        assertTrue(specification.contains("operationId: advanceSimulationClock"));
    }

    @Test
    void executionContextUsesStrictDeterministicInputsAndSharedFactionEnum() throws IOException {
        var specification = readSpecification();

        assertTrue(
                specification.contains(
                        "required: [ schemaVersion, caseKey, seed, entropyVersion, manifestDigest, logicalTime, seats ]"));
        assertTrue(specification.contains("enum: [ 1 ]"));
        assertTrue(specification.contains("format: uint64"));
        assertTrue(specification.contains("pattern: '" + UINT64_DECIMAL_PATTERN + "'"));
        assertTrue(specification.contains("18446744073709551615"));
        assertTrue(specification.contains("enum: [ SHA256_V1 ]"));
        assertTrue(specification.contains("pattern: '^[0-9a-f]{64}$'"));
        assertTrue(specification.contains("minItems: 3"));
        assertTrue(specification.contains("maxItems: 5"));
        assertTrue(specification.contains("$ref: '../shared/enums.yaml#/Faction'"));
    }

    @Test
    void seedConstraintAcceptsOnlyUnsigned64BitDecimalValues() {
        var pattern = Pattern.compile(UINT64_DECIMAL_PATTERN);

        assertTrue(pattern.matcher("0").matches());
        assertTrue(pattern.matcher("42").matches());
        assertTrue(pattern.matcher("18446744073709550000").matches());
        assertTrue(pattern.matcher("18446744073709550999").matches());
        assertTrue(pattern.matcher("18446744073709551600").matches());
        assertTrue(pattern.matcher("18446744073709551609").matches());
        assertTrue(pattern.matcher("18446744073709551615").matches());
        assertFalse(pattern.matcher("18446744073709551616").matches());
        assertFalse(pattern.matcher("-1").matches());
        assertFalse(pattern.matcher("01").matches());
    }

    @Test
    void checkpointModelsNullableLifecycleAndDrainFacts() throws IOException {
        var specification = readSpecification();

        assertTrue(specification.contains("ExecutionCheckpoint:"));
        assertTrue(specification.contains("gameId:"));
        assertTrue(specification.contains("nextDeadline:"));
        assertTrue(specification.contains("nullable: true"));
        assertTrue(specification.contains("enum: [ READY, ACTIVE, TERMINAL ]"));
        assertTrue(specification.contains("outboxPending:"));
        assertTrue(specification.contains("continuationsPending:"));
        assertTrue(specification.contains("dueTimersPending:"));
        assertTrue(specification.contains("sourceWatermarks:"));
    }

    @Test
    void contractDefinesAuthorizationAndEveryNamedError() throws IOException {
        var specification = readSpecification();

        assertTrue(specification.contains("simulation:control"));
        assertTrue(specification.contains("required: [ code ]"));
        for (var errorCode : namedErrorCodes()) {
            assertTrue(specification.contains(errorCode), () -> "Missing error code " + errorCode);
        }
    }

    @Test
    void singleLineDescriptionsContainingColonsAreQuoted() throws IOException {
        var unquoted = Pattern.compile("^\s*description: [^|>\"'].*: .*$");

        for (var line : Files.readAllLines(SPECIFICATION)) {
            assertFalse(unquoted.matcher(line).matches(), "Unquoted description is invalid YAML: " + line);
        }
    }

    private String readSpecification() throws IOException {
        return Files.readString(SPECIFICATION);
    }

    private List<String> namedErrorCodes() {
        return List.of(
                "INVALID_EXECUTION_CONTEXT",
                "INVALID_CLOCK_ADVANCE",
                "AUTHENTICATION_REQUIRED",
                "SIMULATION_CONTROL_FORBIDDEN",
                "SIMULATION_CONTROL_UNAVAILABLE",
                "EXECUTION_CONTEXT_CONFLICT",
                "EXECUTION_NOT_CONFIGURED",
                "IDEMPOTENCY_CONFLICT",
                "STALE_EXECUTION_REVISION",
                "CLOCK_REGRESSION");
    }
}
