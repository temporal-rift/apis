package io.github.temporalrift.timelineevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Contract invariants for the band correction rename.
 *
 * <p>Message names are deliberately NOT unique across modules here: consumer-side redefinitions exist
 * by precedent (e.g. {@code ResolutionStarted} is defined in both session-event and timeline-event, but
 * only ever emitted on one topic). These tests therefore assert the correction rename itself — a
 * distinct name defined once, and the old duplicate marked deprecated — instead of a global
 * uniqueness rule the codebase does not follow.
 */
class MessageNameUniquenessTest {

    private static final List<String> MODULES =
            List.of("session-event", "action-event", "scoring-event", "timeline-event");

    @Test
    void bandCorrectionHasADistinctNameAndTheOldDuplicateIsDeprecated() throws IOException {
        var specification = readModule("timeline-event");

        assertTrue(
                specification.contains("name: BandedProbabilityCorrected"),
                "timeline-event must define the distinct correction message");
        assertTrue(
                specification.contains("BandedProbabilityCorrectedPayload")
                        || specification.contains("payload: { $ref: '#/components/schemas/BandedProbabilityPublishedPayload' }"),
                "the correction must reuse the established band payload shape");

        var oldBlock = messageBlock(specification, "BandedProbabilityPublished");
        assertTrue(oldBlock.contains("x-deprecated: true"), "the old duplicate name must be marked deprecated");
        assertTrue(
                oldBlock.contains("BandedProbabilityCorrected"),
                "the deprecation must point at the replacement name");
    }

    @Test
    void correctionNameIsDefinedInExactlyOneModule() throws IOException {
        List<String> owners = new ArrayList<>();
        for (var module : MODULES) {
            for (var entry : messagesIn(readModule(module))) {
                if (entry.name().equals("BandedProbabilityCorrected") && !entry.deprecated()) {
                    owners.add(module);
                }
            }
        }
        assertEquals(List.of("timeline-event"), owners, "the correction name must have one live owner");
    }

    private record Message(String name, boolean deprecated) {}

    private static String readModule(String module) throws IOException {
        return String.join(
                "\n", Files.readAllLines(Path.of("../" + module + "/src/main/resources/asyncapi/asyncapi.yml")));
    }

    /** Parses the {@code components/messages:} section into (name, deprecated) pairs. */
    private static List<Message> messagesIn(String specification) {
        List<Message> messages = new ArrayList<>();
        boolean inMessages = false;
        String currentName = null;
        boolean currentDeprecated = false;
        for (var line : specification.split("\n")) {
            if (line.equals("  messages:")) {
                inMessages = true;
                continue;
            }
            if (inMessages && line.matches("  \\S.*") && !line.startsWith("   ")) {
                inMessages = false;
            }
            if (!inMessages) {
                continue;
            }
            if (line.matches("    \\w+:")) {
                if (currentName != null) {
                    messages.add(new Message(currentName, currentDeprecated));
                }
                currentName = null;
                currentDeprecated = false;
                continue;
            }
            var nameMatcher = line.matches("      name: (\\w+)") ? line.substring("      name: ".length()) : null;
            if (nameMatcher != null) {
                currentName = nameMatcher;
            }
            if (line.trim().equals("x-deprecated: true")) {
                currentDeprecated = true;
            }
        }
        if (currentName != null) {
            messages.add(new Message(currentName, currentDeprecated));
        }
        return messages;
    }

    /** Returns the raw block of one message definition for targeted assertions. */
    private static String messageBlock(String specification, String key) {
        var start = specification.indexOf("    " + key + ":");
        // Message blocks sit inside components/messages:, so the next 4-space key (exactly 4 spaces
        // followed by a non-space character) ends the block.
        var cursor = start + ("    " + key + ":").length();
        while (true) {
            var end = specification.indexOf("\n    ", cursor);
            if (end < 0 || specification.charAt(end + 5) != ' ') {
                return end < 0 ? specification.substring(start) : specification.substring(start, end);
            }
            cursor = end + 5;
        }
    }
}
