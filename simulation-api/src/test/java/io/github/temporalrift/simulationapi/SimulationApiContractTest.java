package io.github.temporalrift.simulationapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                        "reproduceCase",
                        "listLaneBundles",
                        "listExperimentTemplates")
                .forEach(operation -> assertTrue(specification.contains("operationId: " + operation)));
        assertTrue(specification.contains("name: Idempotency-Key"));
        assertTrue(specification.contains("format: uuid"));
        assertTrue(specification.contains("/api/v1/lane-bundles"));
        assertTrue(specification.contains("/api/v1/experiment-templates"));
        assertTrue(specification.contains("version: 3.2.0"));
    }

    @Test
    void exposesLaneBundleAndTemplateDiscoveryPages() throws IOException {
        var specification = specification();

        assertTrue(specification.contains("LaneBundlePage"));
        assertTrue(specification.contains("ExperimentTemplatePage"));
        assertTrue(specification.contains("ExperimentDefaults"));
        assertTrue(specification.contains("INVALID_REQUEST"));
        assertTrue(specification.contains("operationId: listLaneBundles"));
        assertTrue(specification.contains("operationId: listExperimentTemplates"));
        assertTrue(specification.contains("ascending `effectiveRulesDigest`"));
        assertTrue(specification.contains("ascending `templateId`"));

        var bundle = schema(specification, "    LaneBundle:", "    LaneBundlePage:");
        List.of(
                        "rulesArtifact",
                        "contentArtifact",
                        "effectiveRulesDigest",
                        "effectiveContentDigest",
                        "services",
                        "contracts",
                        "laneCount")
                .forEach(field -> assertTrue(bundle.contains(field), field));
        assertTrue(specification.contains(
                "required: [ templateId, name, description, variantCount, defaults ]"));
    }

    @Test
    void experimentDefaultsMirrorTheManifestFieldForField() throws IOException {
        var specification = specification();
        var manifest = schema(specification, "    ExperimentManifest:", "    Experiment:");
        var defaults = schema(specification, "    ExperimentDefaults:", "    ExperimentTemplate:");

        List.of(
                        "seeds",
                        "playerCounts",
                        "factionSets",
                        "seatRotationMode",
                        "timingMode",
                        "policies",
                        "concurrency",
                        "caseWallTimeoutSeconds",
                        "maxRejectedCandidatesPerWindow")
                .forEach(field -> assertEquals(property(manifest, field), property(defaults, field), field));
    }

    private static String property(String schemaBlock, String field) {
        var marker = "\n        " + field + ":\n";
        var start = schemaBlock.indexOf(marker);
        assertTrue(start >= 0, field);

        var body = schemaBlock.substring(start + marker.length());
        var end = body.length();
        var cursor = 0;
        while (true) {
            var next = body.indexOf("\n        ", cursor);
            if (next < 0) {
                break;
            }
            var after = next + "\n        ".length();
            if (after >= body.length() || body.charAt(after) != ' ') {
                end = next;
                break;
            }
            cursor = next + 1;
        }
        return body.substring(0, end).stripTrailing();
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
    void winTypeIsRequiredButNullableAndNothingElseOnAWinnerIs() throws IOException {
        var specification = specification();
        var winner = schema(specification, "    Winner:", "    FinalScore:");
        var winType = winner.substring(winner.indexOf("        winType:"));
        var others = winner.replace(winType, "");

        assertTrue(winner.contains("required: [ seatIndex, faction, winType ]"));
        assertTrue(winType.contains("nullable: true"));
        assertTrue(winType.contains("enum: [ SCORE_THRESHOLD, FACTION_OBJECTIVE, LAST_PLAYER_STANDING, null ]"));
        assertTrue(winType.contains("null unless the ending is WIN_CONDITION_MET"));
        assertFalse(others.contains("nullable"));
    }

    @Test
    void moduleDeclaresTheGithubPublishingProfile() throws IOException {
        assertTrue(Files.readString(Path.of("pom.xml")).contains("<id>github</id>"));
    }
}
