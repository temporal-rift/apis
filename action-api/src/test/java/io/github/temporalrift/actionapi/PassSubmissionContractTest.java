package io.github.temporalrift.actionapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PassSubmissionContractTest {

    @Test
    void actionTypeAdmitsPassAlongsideCardAndSpecial() throws IOException {
        var specification = readSpecification();

        assertTrue(
                specification.contains("enum: [ CARD, SPECIAL, PASS ]"),
                "ActionType must admit PASS alongside CARD and SPECIAL");
    }

    @Test
    void submitActionDiscriminatorMapsPassToItsOwnBranch() throws IOException {
        var specification = readSpecification();

        assertTrue(
                specification.contains("PASS: '#/components/schemas/PassActionRequest'"),
                "SubmitActionRequest discriminator must map PASS to PassActionRequest");
        assertTrue(
                specification.contains("PassActionRequest:"),
                "PassActionRequest schema must exist");
    }

    @Test
    void actionRoundPassReturnsSubmittedAndConsumesTheSlot() throws IOException {
        var specification = readSpecification();

        assertTrue(
                specification.contains("actionType: PASS"),
                "submitAction success docs must name the explicit pass variant");
        assertTrue(
                specification.contains("including after an"),
                "submitAction conflicts must state that a pass consumes the submission slot");
        assertTrue(
                specification.contains("neutral skip"),
                "submitAction docs must state the pass resolves as the neutral timer-expiry skip");
    }

    @Test
    void paradoxRequestAdmitsAnExplicitPassWithoutCardFields() throws IOException {
        var specification = readSpecification();

        var paradoxBlock = specification.substring(specification.indexOf("ParadoxResolutionCardRequest:"));

        assertTrue(
                paradoxBlock.contains("actionType:"),
                "paradox submission must carry an actionType discriminator field");
        assertTrue(
                paradoxBlock.contains("PASS"),
                "paradox submission must name PASS as the explicit-pass variant");
        assertTrue(
                paradoxBlock.contains("all omitted"),
                "paradox pass docs must state that card and target fields are omitted");
    }

    @Test
    void paradoxPassReturnsSubmittedAndConsumesTheSlot() throws IOException {
        var specification = readSpecification();

        assertTrue(
                specification.contains("or explicit pass submitted"),
                "paradox success docs must cover the pass variant");
        assertTrue(
                specification.contains("including after an accepted pass"),
                "paradox conflicts must state that a pass consumes the phase slot");
        assertTrue(
                specification.contains("mySubmitted: true"),
                "paradox docs must state the pass recovers as submitted");
    }

    @Test
    void callerScopedRecoveryReflectsThePass() throws IOException {
        var specification = readSpecification();

        assertTrue(
                specification.contains("or `PASS`"),
                "MyRoundSubmission docs must list PASS as a recoverable family");
    }

    private static String readSpecification() throws IOException {
        return String.join("\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/action.yml")));
    }
}
