package io.github.temporalrift.actionevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ActionMessagesContractTest {

    private static final Path SPECIFICATION = Path.of("src/main/resources/asyncapi/asyncapi.yml");

    private static final Pattern MESSAGE_NAME = Pattern.compile("(?m)^      name: (\\w+)");

    @Test
    void declaresExactlyTheExpectedMessages() throws IOException {
        var specification = String.join("\n", Files.readAllLines(SPECIFICATION));

        assertEquals(
                Set.of(
                        "DeclarationWindowOpened",
                        "DeclarationOptionsOffered",
                        "ActionRoundStarted",
                        "CardPlayed",
                        "ActionRoundPassed",
                        "ParadoxResolutionCardsOffered",
                        "ParadoxResolutionCardPlayed",
                        "ParadoxResolutionPassed",
                        "SpecialActionPlayed",
                        "PlayerJammed",
                        "InfluenceTraced",
                        "HandCardIntercepted",
                        "ActionRoundTimerExpired",
                        "PlayerSkipped",
                        "ActionRoundClosed",
                        "RoundSummaryPublished",
                        "ActivistDeclarationRecorded",
                        "ExposeSignatureRevealed",
                        "ExposeBehaviorChanged"),
                MESSAGE_NAME.matcher(specification).results().map(match -> match.group(1)).collect(
                        Collectors.toSet()),
                "action-event must declare exactly its expected messages");
    }

    @Test
    void declarationLifecycleSeparatesPublicTimingFromPrivateEligibility() throws IOException {
        var specification = String.join("\n", Files.readAllLines(SPECIFICATION));

        var opening = section(specification, "    DeclarationWindowOpenedPayload:\n", "    DeclarationOptionsOfferedPayload:\n");
        assertTrue(opening.contains("required: [ gameId, eraNumber, expiresAt ]"));
        assertTrue(opening.contains("expiresAt:\n          type: string\n          format: date-time"));
        assertFalse(opening.contains("playerId:"));

        var offer = section(specification, "    DeclarationOptionsOfferedPayload:\n", "    ActionRoundStartedPayload:\n");
        assertTrue(offer.contains("required: [ gameId, eraNumber, playerId, eligibleModes ]"));
        assertTrue(offer.contains("minItems: 1"));
        assertTrue(offer.contains("$ref: '#/components/schemas/ActivistDeclarationMode'"));
    }

    private static String section(String specification, String start, String end) {
        var startIndex = specification.indexOf(start);
        var endIndex = specification.indexOf(end, startIndex + start.length());
        return specification.substring(startIndex, endIndex);
    }
}
