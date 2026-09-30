package io.github.temporalrift.actionevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PlayerDecisionFactsContractTest {

    private static final Path SPECIFICATION = Path.of("src/main/resources/asyncapi/asyncapi.yml");

    @Test
    void publishesTheActionRoundPassOnGameEvents() throws IOException {
        var specification = specification();

        assertTrue(specification.contains(
                "      actionRoundPassed:\n        $ref: '#/components/messages/ActionRoundPassed'"));
        assertTrue(specification.contains("  publishActionRoundPassed:"));
    }

    @Test
    void actionRoundPassConsumesTheSlotAndStaysPubliclyNeutral() throws IOException {
        var message = section(specification(), "    ActionRoundPassed:\n", "    ParadoxResolutionCardsOffered:\n");

        assertTrue(message.contains("Private fact"));
        assertTrue(message.contains("single round slot exactly like CardPlayed or SpecialActionPlayed"));
        assertTrue(message.contains("once every player has submitted or passed"));
        assertTrue(message.contains("neutral skip a timer expiry produces"));
    }

    @Test
    void actionRoundPassPayloadCarriesOnlyItsCoordinates() throws IOException {
        var schema = section(
                specification(), "    ActionRoundPassedPayload:", "    ParadoxResolutionCardsOfferedPayload:");

        assertTrue(schema.contains("additionalProperties: false"));
        assertTrue(schema.contains("required: [ gameId, eraNumber, roundNumber, playerId ]"));
        assertFalse(schema.contains("cardInstanceId"));
        assertFalse(schema.contains("cardType"));
        assertFalse(schema.contains("specialAction"));
        assertFalse(schema.contains("target"));
    }

    @Test
    void publishesTheResolutionCardOfferOnGameEvents() throws IOException {
        var specification = specification();

        assertTrue(specification.contains("      paradoxResolutionCardsOffered:\n"
                + "        $ref: '#/components/messages/ParadoxResolutionCardsOffered'"));
        assertTrue(specification.contains("  publishParadoxResolutionCardsOffered:"));
    }

    @Test
    void resolutionCardOfferIsPrivateAndCoversHandAndReactiveOffer() throws IOException {
        var message = section(specification(), "    ParadoxResolutionCardsOffered:\n", "    ParadoxResolutionCardPlayed:\n");

        assertTrue(message.contains("identifies the sole viewer"));
        assertTrue(message.contains("eligible hand cards and the dealt reactive offer"));
        assertTrue(message.contains("may be empty"));
    }

    @Test
    void resolutionCardOfferPayloadAllowsAnEmptyClosedCardList() throws IOException {
        var schema = section(
                specification(), "    ParadoxResolutionCardsOfferedPayload:", "    EligibleResolutionCard:");

        assertTrue(schema.contains("additionalProperties: false"));
        assertTrue(schema.contains("required: [ gameId, eraNumber, playerId, cards ]"));
        assertTrue(schema.contains("items: { $ref: '#/components/schemas/EligibleResolutionCard' }"));
        assertFalse(schema.contains("minItems"));
    }

    @Test
    void eligibleResolutionCardUsesSharedEnums() throws IOException {
        var schema = section(
                specification(), "    EligibleResolutionCard:", "    ParadoxResolutionCardPlayedPayload:");

        assertTrue(schema.contains("additionalProperties: false"));
        assertTrue(schema.contains("cardType: { $ref: '#/components/schemas/CardType' }"));
        assertTrue(schema.contains("grade: { $ref: '#/components/schemas/CardGrade' }"));
        assertTrue(schema.contains("required: [ cardInstanceId, cardType, grade ]"));
    }

    private static String specification() throws IOException {
        return String.join("\n", Files.readAllLines(SPECIFICATION));
    }

    private static String section(String specification, String start, String end) {
        var startIndex = specification.indexOf(start);
        var endIndex = specification.indexOf(end, startIndex);
        return specification.substring(startIndex, endIndex);
    }
}
