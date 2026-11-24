package io.github.temporalrift.sessionevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WinConditionMetContractTest {

    private static final Path SPECIFICATION = Path.of("src/main/resources/asyncapi/asyncapi.yml");

    @Test
    void winConditionMetCarriesEveryMetCondition() throws IOException {
        var specification = specification();
        var schema = section(specification, "    WinConditionMetPayload:", "    FactionRevealedPlayerFactionResult:");

        assertTrue(
                schema.contains("winConditions:"),
                "WinConditionMetPayload must carry winConditions");
        assertTrue(
                schema.contains("items: { $ref: '#/components/schemas/WinCondition' }"),
                "winConditions must use the shared WinCondition vocabulary");
        assertTrue(schema.contains("minItems: 1"), "winConditions must name at least one condition");
        assertTrue(schema.contains("uniqueItems: true"), "winConditions must not repeat a condition");
        assertTrue(
                schema.contains("ordered SCORE_THRESHOLD before"),
                "winConditions must document the SCORE_THRESHOLD-first ordering");
        assertTrue(
                schema.contains("producer sends it on every event"),
                "winConditions must document that the producer always sends it");
    }

    @Test
    void winTypeKeepsItsSingleConditionMeaning() throws IOException {
        var specification = specification();
        var schema = section(specification, "    WinConditionMetPayload:", "    FactionRevealedPlayerFactionResult:");

        assertTrue(
                schema.contains("required: [ gameId, winnerId, faction, finalScore, winType ]"),
                "winType must stay required and winConditions must stay optional");
        assertTrue(
                schema.contains("SCORE_THRESHOLD when the winner met"),
                "winType must keep its single-condition meaning");
        assertTrue(
                schema.contains("See winConditions"),
                "winType must point to winConditions for every met condition");
    }

    @Test
    void winConditionIsASharedEnumAlias() throws IOException {
        var specification = specification();

        assertTrue(
                specification.contains("    WinCondition:\n      $ref: './shared/enums.yaml#/WinCondition'"),
                "WinCondition must alias the shared enum");
        assertFalse(
                specification.contains("enum: [ SCORE_THRESHOLD, FACTION_OBJECTIVE ]"),
                "the event module must not duplicate the shared vocabulary inline");
    }

    private static String section(String specification, String start, String end) {
        return specification.substring(specification.indexOf(start), specification.indexOf(end));
    }

    private static String specification() throws IOException {
        return String.join("\n", Files.readAllLines(SPECIFICATION));
    }
}
