package io.github.temporalrift.actionevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SpecialActionAvailabilityContractTest {

    private static final Path SPECIFICATION = Path.of("src/main/resources/asyncapi/asyncapi.yml");

    @Test
    void publishesTheAvailabilityReportOnGameEvents() throws IOException {
        var specification = specification();

        assertTrue(specification.contains(
                "      specialActionAvailabilityReported:\n"
                        + "        $ref: '#/components/messages/SpecialActionAvailabilityReported'"));
        assertTrue(specification.contains("  publishSpecialActionAvailabilityReported:"));
    }

    @Test
    void reportIsAddressedToThePlayerAsSoleViewer() throws IOException {
        var message = section(specification(), "    SpecialActionAvailabilityReported:\n", "    CardPlayed:\n");

        assertTrue(message.contains("headers: { $ref: '#/components/schemas/EventEnvelopeHeaders' }"));
        assertTrue(message.contains(
                "payload: { $ref: '#/components/schemas/SpecialActionAvailabilityReportedPayload' }"));
        assertFalse(message.contains("specials:"));
    }

    @Test
    void payloadCarriesTheRoundAndExactlyThePlayersThreeSpecials() throws IOException {
        var payload = section(specification(),
                "    SpecialActionAvailabilityReportedPayload:\n", "    ReportedSpecialAvailability:\n");

        assertTrue(payload.contains("additionalProperties: false"));
        assertTrue(payload.contains("eraNumber: { type: integer, minimum: 1 }"));
        assertTrue(payload.contains("roundNumber: { type: integer, minimum: 1, maximum: 3 }"));
        assertTrue(payload.contains("minItems: 3"));
        assertTrue(payload.contains("maxItems: 3"));
        assertTrue(payload.contains("items: { $ref: '#/components/schemas/ReportedSpecialAvailability' }"));
        assertTrue(payload.contains("required: [ gameId, eraNumber, roundNumber, playerId, specials ]"));
    }

    @Test
    void entryRequiresAReasonOnlyWhenUnavailable() throws IOException {
        var entry = section(specification(),
                "    ReportedSpecialAvailability:\n", "    CardPlayedPayload:\n");

        assertTrue(entry.contains("specialAction: { $ref: '#/components/schemas/SpecialAction' }"));
        assertTrue(entry.contains("available: { type: boolean }"));
        assertTrue(entry.contains("$ref: '#/components/schemas/SpecialActionUnavailableReason'"));
        assertTrue(entry.contains("required: [ specialAction, available ]"));
        assertTrue(entry.contains("oneOf:"));
        assertTrue(entry.contains("enum: [ false ]"));
        assertTrue(entry.contains("enum: [ true ]"));
        assertTrue(entry.contains("required: [ unavailableReason ]"));
        assertTrue(entry.contains("not:\n            required: [ unavailableReason ]"));
    }

    @Test
    void availabilityReasonsComeFromTheSharedEnum() throws IOException {
        var specification = specification();

        assertTrue(specification.contains(
                "    SpecialActionUnavailableReason:\n"
                        + "      $ref: './shared/enums.yaml#/SpecialActionUnavailableReason'"));
        assertFalse(specification.contains("enum: [ JAMMED"));
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
