package io.github.temporalrift.simulationapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class SimulationApiContractTest {

    @Test
    void exposesEveryWorkbenchOperation() throws IOException {
        var specification = specification();

        List.of(
                        "createExperiment",
                        "startRun",
                        "getRun",
                        "cancelRun",
                        "resumeRun",
                        "getRunReport",
                        "createComparison",
                        "getComparison",
                        "getCase",
                        "getCaseReplay",
                        "reproduceCase")
                .forEach(operation -> assertTrue(specification.contains("operationId: " + operation)));
        assertTrue(specification.contains("name: Idempotency-Key"));
        assertTrue(specification.contains("format: uuid"));
    }

    @Test
    void freezesACompletePortableExperimentManifest() throws IOException {
        var specification = specification();
        var manifest = schema(specification, "ExperimentManifest:", "    Experiment:");

        List.of(
                        "schemaVersion",
                        "variants",
                        "services",
                        "contracts",
                        "policies",
                        "seeds",
                        "playerCounts",
                        "factionSets",
                        "seatRotationMode",
                        "timingMode",
                        "caseWallTimeoutSeconds",
                        "maxRejectedCandidatesPerWindow")
                .forEach(field -> assertTrue(manifest.contains("- " + field)));
        assertTrue(manifest.contains("enum: [ CYCLIC ]"));
        assertTrue(manifest.contains("enum: [ LOGICAL ]"));
        assertTrue(specification.contains("portable content-addressed artifact reference"));
        assertTrue(specification.contains("credentials nor a\n        machine-local path"));
    }

    @Test
    void keepsRetriesReportsAndReplaysSemanticallyDistinct() throws IOException {
        var specification = specification();

        assertTrue(specification.contains("enum: [ QUEUED, RUNNING, INTERRUPTED, CANCELLING, CANCELLED, COMPLETED, FAILED ]"));
        assertTrue(specification.contains("Retained successful\n        cases remain one logical sample"));
        assertTrue(specification.contains("INCOMPARABLE_RUNS"));
        assertTrue(specification.contains("BLOCK_BOOTSTRAP"));
        assertTrue(specification.contains("enum: [ PLAYER, OBSERVER ]"));
        assertTrue(specification.contains("OBSERVER perspective additionally requires `simulation:observe`"));
        assertTrue(specification.contains("does not create a new\n        research sample"));
    }

    @Test
    void exposesOnlyThePublishedTerminalAndSharedEnumVocabulary() throws IOException {
        var specification = specification();

        assertTrue(specification.contains("$ref: '../shared/enums.yaml#/Faction'"));
        assertTrue(specification.contains("$ref: '../shared/enums.yaml#/CardType'"));
        assertTrue(specification.contains("WIN_CONDITION_MET, TIMELINE_COLLAPSED, TIMELINE_STABILIZED"));
        assertTrue(specification.contains("CONTRACT_MISMATCH"));
        assertTrue(specification.contains("CONFIGURATION_DRIFT"));
        assertTrue(specification.contains("RESOURCE_NOT_FOUND"));
        assertTrue(specification.contains("INSUFFICIENT_SCOPE"));
    }

    private static String specification() throws IOException {
        return Files.readString(Path.of("src/main/resources/openapi/v1/simulation.yml")).replace("\r\n", "\n");
    }

    private static String schema(String specification, String start, String end) {
        return specification.substring(specification.indexOf(start), specification.indexOf(end));
    }

    @Test
    void moduleDeclaresTheGithubPublishingProfile() throws IOException {
        assertTrue(Files.readString(Path.of("pom.xml")).contains("<id>github</id>"));
    }
}
