package io.github.temporalrift.projectionapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class DecisionWindowContractTest {

    private static final Path SPECIFICATION = Path.of("src/main/resources/openapi/v1/projection.yml");

    @Test
    void submissionsNameTheirWindowAndChoice() throws IOException {
        var specification = specification();
        var submission = section(specification, "    MySubmission:\n", "    SubmittedCard:\n");

        assertTrue(specification.contains(
                "    SubmissionWindow:\n      type: string\n      enum: [ HAND_SELECTION, DECLARATION, ACTION, PARADOX_RESOLUTION ]"));
        assertTrue(specification.contains(
                "    SubmissionChoice:\n      type: string\n      enum: [ CARD, SPECIAL, PASS ]"));
        assertTrue(submission.contains("required: [ eraNumber, window, status ]"));
        assertTrue(submission.contains("$ref: '#/components/schemas/SubmissionWindow'"));
        assertTrue(submission.contains("$ref: '#/components/schemas/SubmissionChoice'"));
        assertTrue(submission.contains("CARD, SPECIAL or PASS for ACTION entries; CARD or PASS for"));
        assertFalse(specification.contains("PARADOX_CARD"));
        assertFalse(submission.contains("kind:"));
        assertFalse(submission.contains("actionType:"));
    }

    @Test
    void submissionsCarryOnlyTheCallersOwnDecisionDetail() throws IOException {
        var specification = specification();
        var submission = section(specification, "    MySubmission:\n", "    SubmittedCard:\n");

        assertTrue(submission.contains("$ref: '#/components/schemas/SubmittedCard'"));
        assertTrue(submission.contains("present only when choice is CARD"));
        assertTrue(submission.contains("$ref: '#/components/schemas/SpecialAction'"));
        assertTrue(submission.contains("present only when choice is SPECIAL"));
        assertTrue(submission.contains("$ref: '#/components/schemas/SubmissionTargets'"));
        assertTrue(submission.contains("absent for a pass"));

        var card = section(specification, "    SubmittedCard:\n", "    SubmissionTargets:\n");
        assertTrue(card.contains("required: [ cardInstanceId, cardType, grade ]"));
        assertTrue(card.contains("disguiseCategory:\n          $ref: '#/components/schemas/CardCategory'"));

        var targets = section(specification, "    SubmissionTargets:\n", "    EligibleResolutionCard:\n");
        for (var target : new String[] {
            "targetEventId:", "targetEventIds:", "targetOutcomeId:", "sourceEventId:", "sourceOutcomeId:",
            "targetPlayerId:", "targetPlayerIds:"
        }) {
            assertTrue(targets.contains(target), target);
        }
        assertFalse(targets.contains("required:"));

        assertTrue(section(specification, "        mySubmissions:\n", "        mySpecialBudgets:\n")
                .contains("Never includes another player's decisions"));
    }

    @Test
    void phaseContextCarriesPublicProgressAndAffectedEvents() throws IOException {
        var specification = specification();
        var phaseContext = section(specification, "    PhaseContext:\n", "    SubmissionProgress:\n");

        assertTrue(phaseContext.contains(
                "        actionRoundProgress:\n          $ref: '#/components/schemas/SubmissionProgress'"));
        assertTrue(phaseContext.contains("present only while an action round is open"));
        assertTrue(phaseContext.contains(
                "        paradoxResolutionProgress:\n          $ref: '#/components/schemas/SubmissionProgress'"));
        assertTrue(phaseContext.contains("present only while paradoxOpen is true"));
        assertTrue(phaseContext.contains("        affectedEventIds:\n          type: array"));
        assertTrue(phaseContext.contains("is true; absent otherwise"));
        assertTrue(phaseContext.contains("required: [ declarationOpen, paradoxOpen ]"));

        var progress = section(specification, "    SubmissionProgress:\n", "    PublicBandOutcome:\n");
        assertTrue(progress.contains("required: [ submittedCount, totalPlayers, pendingPlayerIds ]"));
        assertTrue(progress.contains("never what anyone submitted"));
        assertFalse(progress.contains("cardType"));
        assertFalse(progress.contains("actionType"));
    }

    @Test
    void stateCarriesTheCallersOwnEligibleResolutionCards() throws IOException {
        var specification = specification();
        var eligibleCards = section(specification, "        myEligibleResolutionCards:\n", "        myScore:\n");

        assertTrue(eligibleCards.contains("items:\n            $ref: '#/components/schemas/EligibleResolutionCard'"));
        assertTrue(eligibleCards.contains("eligible hand\n            cards plus the dealt Stabilize and Detonate offer"));
        assertTrue(eligibleCards.contains("Present, possibly empty, only while the phase is open"));
        assertTrue(eligibleCards.contains("neither submitted nor passed; absent otherwise"));
        assertTrue(eligibleCards.contains("Never carries another participant's"));
        assertFalse(section(specification, "    PlayerGameStateResponse:\n", "      properties:\n")
                .contains("myEligibleResolutionCards"));

        var card = section(specification, "    EligibleResolutionCard:\n", "    SpecialBudget:\n");
        assertTrue(card.contains("required: [ cardInstanceId, cardType, grade ]"));
        assertTrue(card.contains("cardType:\n          $ref: '#/components/schemas/CardType'"));
        assertTrue(card.contains("grade:\n          $ref: '#/components/schemas/CardGrade'"));
    }

    @Test
    void stateCarriesPublicDeclarationTimingAndCallerScopedModes() throws IOException {
        var specification = specification();
        var deadlines = section(specification, "    Deadlines:\n", "    PhaseContext:\n");
        assertTrue(deadlines.contains("        declarationExpiresAt:\n          type: string\n          format: date-time\n          nullable: true"));
        assertTrue(deadlines.contains("Present only while declarationOpen is true"));

        var eligibleModes = section(specification, "        myEligibleDeclarationModes:\n", "        myScore:\n");
        assertTrue(eligibleModes.contains("Present, possibly empty, only\n            while declarationOpen is true"));
        assertTrue(eligibleModes.contains("never carries another participant's eligibility or faction"));
        assertTrue(eligibleModes.contains("$ref: '#/components/schemas/ActivistDeclarationMode'"));

        var mode = section(specification, "    ActivistDeclarationMode:\n", "    SpecialBudget:\n");
        assertTrue(mode.contains("enum: [ RALLY, MOMENTUM ]"));
    }

    @Test
    void cardFactionAndSpecialValuesUseTheSharedEnums() throws IOException {
        var specification = specification();

        assertTrue(specification.contains("    CardType:\n      $ref: '../shared/enums.yaml#/CardType'"));
        assertTrue(specification.contains("    Faction:\n      $ref: '../shared/enums.yaml#/Faction'"));
        assertTrue(specification.contains("    SpecialAction:\n      $ref: '../shared/enums.yaml#/SpecialAction'"));
        assertFalse(Pattern.compile("(?m)^\\s+(cardType|faction|myFaction|specialAction):\\n\\s+type: string")
                .matcher(specification)
                .find());
        assertFalse(specification.contains("enum: [ ERASERS, PROPHETS, REVISIONISTS, WEAVERS, ACTIVISTS ]"));
        assertTrue(section(specification, "        mySpecialActions:\n", "        myJammedUntilRound:\n")
                .contains("items:\n            $ref: '#/components/schemas/SpecialAction'"));
    }

    private static String specification() throws IOException {
        return String.join("\n", Files.readAllLines(SPECIFICATION));
    }

    private static String section(String specification, String start, String end) {
        var startIndex = specification.indexOf(start);
        var endIndex = specification.indexOf(end, startIndex + start.length());
        return specification.substring(startIndex, endIndex);
    }
}
