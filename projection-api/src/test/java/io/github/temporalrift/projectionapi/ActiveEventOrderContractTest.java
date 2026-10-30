package io.github.temporalrift.projectionapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ActiveEventOrderContractTest {

    @Test
    void activeEventsAreListedInTheEraRevealOrder() throws IOException {
        var specification = String.join(
                "\n", Files.readAllLines(Path.of("src/main/resources/openapi/v1/projection.yml")));

        var start = specification.indexOf("        activeEvents:");
        var activeEvents = specification.substring(start, specification.indexOf("        players:", start));
        assertTrue(activeEvents.contains("reveal order"));
        assertTrue(activeEvents.contains("the order of the era's EventsDrawn events"));
        assertTrue(activeEvents.contains("carried-over events included"));
        assertTrue(activeEvents.contains("without reordering the rest"));
        assertTrue(activeEvents.contains("$ref: '#/components/schemas/ActiveEvent'"));
    }
}
