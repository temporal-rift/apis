package io.github.temporalrift.timelineevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Contract invariants for the erasures that changed an event's winning outcome.
 *
 * <p>Timeline decides, per accepted erasure, whether the same draw roll would have selected a different
 * winner without that erasure, and publishes the erased outcome ids with the event's outcome. The list is
 * optional so a consumer built against the previous contract still reads a new message.
 */
class OutcomeAppliedConsequentialErasuresContractTest {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Test
    void asyncApiModelsConsequentialErasuresAsUniqueOutcomeIds() throws IOException {
        var payload = payloadBlock();

        assertTrue(payload.contains("        consequentialErasures:"));
        var field = payload.substring(payload.indexOf("        consequentialErasures:"));
        assertTrue(field.contains("type: array"));
        assertTrue(field.contains("uniqueItems: true"));
        assertTrue(field.contains("items: { type: string, format: uuid }"));
        assertFalse(field.contains("default: [ ]"),
                "an absent field means an older producer, not an empty list, so the schema must not default it");
        assertTrue(field.contains("from a direct"));
        assertTrue(field.contains("carried-forward Cascade"));
        assertTrue(field.contains("targetEventId and targetOutcomeId"));
    }

    @Test
    void messageDescriptionDistinguishesAnEmptyListFromAnAbsentField() throws IOException {
        var message = messageBlock();

        assertTrue(message.contains("empty list means no erasure changed the winner"));
        assertTrue(message.contains("absent field"));
        assertTrue(message.contains("keeps its previous behaviour"));
    }

    @Test
    void consequentialErasuresIsACompatibleOptionalAddition() throws IOException {
        var payload = payloadBlock();
        var required = payload.substring(payload.indexOf("      required: ["));

        assertFalse(required.contains("consequentialErasures"),
                "consequentialErasures must stay optional so consumers of the previous contract still read new messages");
    }

    @Test
    void acceptsAListOfAtMostOneIdPerErasure() {
        assertTrue(isValidConsequentialErasures(List.of(UUID.randomUUID().toString())));
        assertTrue(isValidConsequentialErasures(
                List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString())));
    }

    @Test
    void anEmptyListAndAnAbsentFieldAreBothValidButMeanDifferentThings() {
        assertTrue(isValidConsequentialErasures(List.of()));
        assertTrue(isValidConsequentialErasures(null));
    }

    @Test
    void rejectsANonUuidEntry() {
        assertFalse(isValidConsequentialErasures(List.of("not-a-uuid")));
    }

    @Test
    void rejectsADuplicatedErasure() {
        var outcomeId = UUID.randomUUID().toString();

        assertFalse(isValidConsequentialErasures(List.of(outcomeId, outcomeId)));
    }

    @Test
    void redeliveryCarriesTheIdenticalList() {
        var published = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        var redelivered = List.copyOf(published);

        assertEquals(redelivered, published, "redelivery must carry the identical list");
    }

    private static boolean isValidConsequentialErasures(List<String> erasedOutcomeIds) {
        if (erasedOutcomeIds == null) {
            return true;
        }
        return erasedOutcomeIds.stream().allMatch(id -> UUID_FORMAT.matcher(id).matches())
                && erasedOutcomeIds.stream().distinct().count() == erasedOutcomeIds.size();
    }

    private static String messageBlock() throws IOException {
        var specification = Files.readString(Path.of("src/main/resources/asyncapi/asyncapi.yml"));
        var start = specification.indexOf("    OutcomeApplied:");
        var end = specification.indexOf("    EraResolutionCompleted:", start);
        return specification.substring(start, end);
    }

    private static String payloadBlock() throws IOException {
        var specification = Files.readString(Path.of("src/main/resources/asyncapi/asyncapi.yml"));
        var start = specification.indexOf("    OutcomeAppliedPayload:");
        var end = specification.indexOf("    EraResolutionTerminalState:", start);
        return specification.substring(start, end);
    }
}
