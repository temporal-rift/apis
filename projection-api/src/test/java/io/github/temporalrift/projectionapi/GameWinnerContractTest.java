package io.github.temporalrift.projectionapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GameWinnerContractTest {

    @Test
    void gameWinnerCarriesEveryMetCondition() throws IOException {
        var specification = specification();
        var winner = specification.substring(
                specification.indexOf("    GameWinner:"),
                specification.indexOf("    FinalScore:"));

        assertTrue(winner.contains("required: [ playerId ]"), "GameWinner keeps playerId as its only required field");
        assertTrue(winner.contains("        winConditions:"), "GameWinner must carry winConditions");
        assertTrue(
                winner.contains("items: { $ref: '#/components/schemas/WinCondition' }"),
                "winConditions must use the shared WinCondition vocabulary");
        assertTrue(winner.contains("minItems: 1"), "winConditions must name at least one condition");
        assertTrue(winner.contains("uniqueItems: true"), "winConditions must not repeat a condition");
        assertTrue(
                winner.contains("ordered SCORE_THRESHOLD before FACTION_OBJECTIVE"),
                "winConditions must document the SCORE_THRESHOLD-first ordering");
    }

    @Test
    void winConditionsIsPresentExactlyForNormalVictoryWins() throws IOException {
        var specification = specification();
        var winner = specification.substring(
                specification.indexOf("    GameWinner:"),
                specification.indexOf("    FinalScore:"));
        var winConditions = winner.substring(winner.indexOf("        winConditions:"));

        assertTrue(
                winConditions.contains("Present exactly when winType is SCORE_THRESHOLD or FACTION_OBJECTIVE"),
                "winConditions must document its presence rule");
        assertTrue(
                winConditions.contains("absent for"),
                "winConditions must document when it is absent");
        assertTrue(winConditions.contains("LAST_PLAYER_STANDING"), "presence rule must name LAST_PLAYER_STANDING");
    }

    @Test
    void winConditionIsASharedEnumAlias() throws IOException {
        var specification = specification();

        assertTrue(
                specification.contains("    WinCondition:\n      $ref: '../shared/enums.yaml#/WinCondition'"),
                "WinCondition must alias the shared enum");
        assertFalse(
                winnerBlock(specification).contains("enum: [ SCORE_THRESHOLD, FACTION_OBJECTIVE ]"),
                "GameWinner must not duplicate the shared vocabulary inline");
    }

    private static String winnerBlock(String specification) {
        return specification.substring(
                specification.indexOf("    GameWinner:"),
                specification.indexOf("    FinalScore:"));
    }

    private static String specification() throws IOException {
        return String.join(
                "\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/projection.yml")));
    }
}
