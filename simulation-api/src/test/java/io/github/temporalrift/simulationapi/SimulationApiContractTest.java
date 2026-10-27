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
                        "listExperimentTemplates",
                        "listBalanceCriteria",
                        "getBalanceCriterion",
                        "getRunEvaluation",
                        "listParameterSpaces",
                        "getParameterSpace",
                        "previewVariantRules",
                        "getVariantRules",
                        "createCampaign",
                        "listCampaigns",
                        "getCampaign",
                        "pauseCampaign",
                        "resumeCampaign",
                        "cancelCampaign",
                        "setCampaignParallelism",
                        "getCampaignLog",
                        "startConfirmation",
                        "startFidelityCheck",
                        "listFidelityChecks",
                        "getFidelityCheck",
                        "listFidelityCases",
                        "getSimulatorFidelity")
                .forEach(operation -> assertTrue(specification.contains("operationId: " + operation)));
        assertTrue(specification.contains("name: Idempotency-Key"));
        assertTrue(specification.contains("format: uuid"));
        assertTrue(specification.contains("/api/v1/lane-bundles"));
        assertTrue(specification.contains("/api/v1/experiment-templates"));
        assertTrue(specification.contains("/api/v1/balance-criteria"));
        assertTrue(specification.contains("/api/v1/runs/{runId}/evaluation"));
        assertTrue(specification.contains("/api/v1/parameter-spaces"));
        assertTrue(specification.contains("/api/v1/variant-rules-previews"));
        assertTrue(specification.contains("/api/v1/experiments/{experimentId}/variants/{label}/rules"));
        assertTrue(specification.contains("/api/v1/campaigns/{campaignId}/pause"));
        assertTrue(specification.contains("/api/v1/campaigns/{campaignId}/resume"));
        assertTrue(specification.contains("/api/v1/campaigns/{campaignId}/cancel"));
        assertTrue(specification.contains("/api/v1/campaigns/{campaignId}/parallelism"));
        assertTrue(specification.contains("/api/v1/campaigns/{campaignId}/log"));
        assertTrue(specification.contains("/api/v1/campaigns/{campaignId}/confirmation"));
        assertTrue(specification.contains("version: 4.2.0"));
    }

    @Test
    void exposesTheSearchCampaignContract() throws IOException {
        var specification = specification();

        List.of(
                        "CampaignDefinition",
                        "CampaignBaseline",
                        "CampaignFailure",
                        "SearchAlgorithm",
                        "Campaign",
                        "CampaignState",
                        "CampaignList",
                        "CampaignLogEntry",
                        "CampaignLogDecision",
                        "CampaignLogReason",
                        "CampaignLogEvidence",
                        "CampaignEvaluation",
                        "CampaignLogPage",
                        "CampaignConfirmation",
                        "ConfirmationRun",
                        "CampaignParallelismUpdate")
                .forEach(schema -> assertTrue(specification.contains("    " + schema + ":"), schema));

        assertTrue(specification.contains(
                "enum: [ QUEUED, RUNNING, PAUSED, SEARCH_COMPLETED, CONFIRMING, COMPLETED, CANCELLED, FAILED ]"));
        assertTrue(specification.contains("enum: [ GENERATED, KEPT, DISCARDED, SHORT_LISTED ]"));
        assertTrue(specification.contains("enum: [ GATE_FAILS, DOMINATED, NOT_IN_TOP_FRACTION, BUDGET_EXHAUSTED ]"));
        assertTrue(specification.contains("enum: [ SUCCESSIVE_HALVING_V1 ]"));
        assertTrue(specification.contains("INVALID_CAMPAIGN_STATE"));
        assertTrue(specification.contains("already `PAUSED` campaign returns that"));
        assertTrue(specification.contains("already `RUNNING` campaign returns that"));
        assertTrue(specification.contains("already `CANCELLED`"));
        assertTrue(specification.contains("SEED_OVERLAP"));
        assertTrue(specification.contains("EQUAL_MASTER_SEEDS"));
        assertTrue(specification.contains("UNKNOWN_CRITERION"));

        var definition = schema(specification, "    CampaignDefinition:", "    CampaignState:");
        assertTrue(definition.contains("default: 14400"));
        assertTrue(definition.contains("default: 4"));
        assertTrue(definition.contains("budgetSeconds"));
        assertTrue(definition.contains("parallelism"));

        var entry = schema(specification, "    CampaignLogEntry:", "    CampaignLogPage:");
        List.of("sequence", "round", "candidateId", "overrides", "effectiveRulesDigest", "seeds",
                        "gamesSpent", "evaluation", "decision", "reason", "evidence")
                .forEach(field -> assertTrue(entry.contains(field), field));

        var evidence = schema(specification, "    CampaignLogEvidence:", "    CampaignLogEntry:");
        assertTrue(evidence.contains("lineIds"));
        assertTrue(evidence.contains("dominatedBy"));

        var confirmation = schema(specification, "    CampaignConfirmation:", "    CampaignParallelismUpdate:");
        assertTrue(confirmation.contains("seedCount"));
        assertTrue(confirmation.contains("seedOverlap"));
        assertTrue(specification.contains("literal `baseline`"));

        assertTrue(specification.contains("examples:"));
        assertTrue(specification.contains("summary: A search paused by the designer"));
        assertTrue(specification.contains("decision: DISCARDED"));
        assertTrue(specification.contains("reason: GATE_FAILS"));
        assertTrue(specification.contains("lineIds: [ 'G-01' ]"));
    }

    @Test
    void campaignRepeatsTheDefinitionFieldForField() throws IOException {
        var specification = specification();
        var definition = schema(specification, "    CampaignDefinition:", "    CampaignState:");
        var campaign = schema(specification, "    Campaign:", "    CampaignList:");

        List.of(
                        "name",
                        "baseline",
                        "parameterSpaceVersion",
                        "criterionVersion",
                        "policies",
                        "playerCounts",
                        "searchMasterSeed",
                        "confirmationMasterSeed",
                        "algorithm")
                .forEach(field -> assertEquals(property(definition, field), property(campaign, field), field));
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
        var marker = "\n        " + field + ":";
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
    void exposesBalanceCriterionAndEvaluationSchemas() throws IOException {
        var specification = specification();

        assertTrue(specification.contains("AttemptFailureCode"));
        assertTrue(specification.contains(
                "enum: [ RUNNER_TIMEOUT, EXECUTION_FAILED, CONTRACT_MISMATCH, CONFIGURATION_DRIFT, POLICY_EXHAUSTED ]"));
        assertTrue(specification.contains("failureCode"));
        assertTrue(specification.contains("failure_share"));
        assertTrue(specification.contains("BalanceCriterion"));
        assertTrue(specification.contains("CriterionLine"));
        assertTrue(specification.contains("CriterionDimension"));
        assertTrue(specification.contains("CriterionEvidence"));
        assertTrue(specification.contains("BalanceCriterionCatalog"));
        assertTrue(specification.contains("RunEvaluation"));
        assertTrue(specification.contains("EvaluationValidity"));
        assertTrue(specification.contains("ConfirmationSample"));
        assertTrue(specification.contains("EvaluatedLine"));
        assertTrue(specification.contains("WEIGHTED_SUM_OF_SQUARED_DEVIATIONS"));
        assertTrue(specification.contains("RATIO_TO_WIN_REFERENCE"));
        assertTrue(specification.contains("SUM_OF_FAILURE_CODES"));
        assertTrue(specification.contains("UNFINISHED_CASES"));
        assertTrue(specification.contains("EXECUTION_FAILED_SHARE"));
        assertTrue(specification.contains("ALL_PLAYERS_ABANDONED"));
        assertTrue(specification.contains("CONFIRMED"));
        assertTrue(specification.contains("UNCONFIRMED"));
        assertTrue(specification.contains("INCONCLUSIVE"));

        var line = schema(specification, "    CriterionLine:", "    CriterionEvidence:");
        assertTrue(line.contains("pattern: '^[GL]-[0-9]{2}$'"));
        assertTrue(line.contains("weight"));
        assertTrue(line.contains("rationale"));

        var band = schema(specification, "    Band:", "    CriterionDerivation:");
        List.of("lower", "upper", "scale").forEach(field -> assertTrue(band.contains(field), field));
    }

    @Test
    void reportCsvDistinguishesFailureCodesAndRaisesTheFormatVersion() throws IOException {
        var specification = specification();
        var report = schema(specification, "    Report:", "    ComparisonSide:");
        var comparison = schema(specification, "    Comparison:", "    ProblemDetail:");

        assertTrue(specification.contains(
                "paradoxType, endReason, quantile, failureCode, unit, value, numerator, denominator,"));
        assertTrue(specification.contains(
                "paradoxType, endReason, quantile, unit, baselineValue, candidateValue, difference,"));
        assertTrue(report.contains("enum: [ 2 ]"));
        assertFalse(report.contains("enum: [ 1 ]"));
        assertTrue(comparison.contains("enum: [ 1 ]"));
        assertFalse(comparison.contains("enum: [ 2 ]"));
    }

    @Test
    void moduleDeclaresTheGithubPublishingProfile() throws IOException {
        assertTrue(Files.readString(Path.of("pom.xml")).contains("<id>github</id>"));
    }
}
