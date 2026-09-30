package io.github.temporalrift.actionevent;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
