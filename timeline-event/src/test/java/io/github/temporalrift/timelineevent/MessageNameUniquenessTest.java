package io.github.temporalrift.timelineevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Contract invariants for the band rename.
 *
 * <p>Message names are deliberately NOT unique across modules here: consumer-side redefinitions exist
 * by precedent (e.g. {@code ResolutionStarted} is defined in both session-event and timeline-event, but
 * only ever emitted on one topic). These tests therefore assert the rename itself — a distinct name
 * defined once, and the old duplicate marked deprecated — instead of a global uniqueness rule the
 * codebase does not follow.
 */
class MessageNameUniquenessTest {

    private static final List<String> MODULES =
            List.of("session-event", "action-event", "scoring-event", "timeline-event");

    private static final Pattern MESSAGE_KEY = Pattern.compile("(?m)^    (\\w+):");
    private static final Pattern MESSAGE_NAME = Pattern.compile("(?m)^      name: (\\w+)");

    @Test
    void adjustedBandsHasADistinctNameAndTheOldDuplicateIsDeprecated() throws IOException {
        var specification = readModule("timeline-event");

        assertTrue(
                specification.contains("name: AdjustedBandsPublished"),
                "timeline-event must define the distinct adjusted-bands message");

        var oldBlock = messageBlock(specification, "BandedProbabilityPublished");
        assertTrue(oldBlock.contains("x-deprecated: true"), "the old duplicate name must be marked deprecated");
        assertTrue(
                oldBlock.contains("AdjustedBandsPublished"),
                "the deprecation must point at the replacement name");
    }

    @Test
    void adjustedBandsNameIsDefinedInExactlyOneModule() throws IOException {
        List<String> owners = new ArrayList<>();
        for (var module : MODULES) {
            for (var entry : messagesIn(readModule(module))) {
                if (entry.name().equals("AdjustedBandsPublished") && !entry.deprecated()) {
                    owners.add(module);
                }
            }
        }
        assertEquals(List.of("timeline-event"), owners, "the adjusted-bands name must have one live owner");
    }

    private record Message(String name, boolean deprecated) {}

    private static String readModule(String module) throws IOException {
        return String.join(
                "\n", Files.readAllLines(Path.of("../" + module + "/src/main/resources/asyncapi/asyncapi.yml")));
    }

    /** Parses the {@code components/messages:} section into (name, deprecated) pairs. */
    private static List<Message> messagesIn(String specification) {
        var messagesSection = section(specification, "components:", "  messages:", "  schemas:");
        List<Message> messages = new ArrayList<>();
        for (var block : blocks(messagesSection)) {
            var name = MESSAGE_NAME.matcher(block);
            if (name.find()) {
                messages.add(new Message(name.group(1), block.contains("x-deprecated: true")));
            }
        }
        return messages;
    }

    /** Returns the raw block of one message definition for targeted assertions. */
    private static String messageBlock(String specification, String key) {
        var messagesSection = section(specification, "components:", "  messages:", "  schemas:");
        for (var block : blocks(messagesSection)) {
            if (block.startsWith("    " + key + ":")) {
                return block;
            }
        }
        throw new AssertionError("message " + key + " not found in components/messages:");
    }

    /** Extracts the text between a section header and the next sibling header. */
    private static String section(String specification, String parent, String header, String nextSibling) {
        var parentAt = specification.indexOf("\n" + parent);
        var headerAt = specification.indexOf("\n" + header, parentAt);
        var endAt = specification.indexOf("\n" + nextSibling, headerAt);
        return endAt < 0 ? specification.substring(headerAt) : specification.substring(headerAt, endAt);
    }

    /** Splits a section into blocks keyed by 4-space-indented entries. */
    private static List<String> blocks(String sectionText) {
        var keys = new ArrayList<String>();
        var starts = new ArrayList<Integer>();
        Matcher key = MESSAGE_KEY.matcher(sectionText);
        while (key.find()) {
            keys.add(key.group(1));
            starts.add(key.start());
        }
        List<String> blocks = new ArrayList<>();
        for (var index = 0; index < keys.size(); index++) {
            var end = index + 1 < keys.size() ? starts.get(index + 1) : sectionText.length();
            blocks.add(sectionText.substring(starts.get(index), end));
        }
        return blocks;
    }
}
