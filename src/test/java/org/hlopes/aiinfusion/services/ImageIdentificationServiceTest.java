package org.hlopes.aiinfusion.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class ImageIdentificationServiceTest {

    @Inject
    ImageIdentificationService service;

    @Test
    public void testParseCleanJson() {
        var result =
                service.parseVisionContent("{\"title\": \"No Time to Die\", \"year\": 2021, \"mediaType\": \"MOVIE\", "
                        + "\"confidence\": \"high\", \"description\": \"Bond returns for one last mission.\"}");

        assertNotNull(result);
        assertEquals("No Time to Die", result.title());
        assertEquals(2021, result.year());
        assertEquals("high", result.confidence());
    }

    @Test
    public void testParseFencedJson() {
        var result = service.parseVisionContent(
                "```json\n{\"title\": \"Skyfall\", \"year\": 2012, \"mediaType\": \"MOVIE\", "
                        + "\"confidence\": \"high\", \"description\": \"Bond defends M.\"}\n```");

        assertNotNull(result);
        assertEquals("Skyfall", result.title());
    }

    @Test
    public void testProseIsNotIdentified() {
        // cardvault-500m answers the JSON prompt in prose; that must not parse as a match
        assertNull(service.parseVisionContent("A movie or TV show is identified by the text \"No Time to Die\"."));
        assertNull(service.parseVisionContent(""));
        assertNull(service.parseVisionContent(null));
    }

    @Test
    public void testLowConfidencePassesThrough() {
        var result = service.parseVisionContent("{\"title\": \"Skynet\", \"mediaType\": \"MOVIE\", "
                + "\"confidence\": \"low\", \"description\": \"Unclear.\"}");

        assertNotNull(result);
        assertEquals("low", result.confidence());
    }

    @Test
    public void testBlankObservationIsNotIdentified() {
        assertNull(service.reasonTitle(null));
        assertNull(service.reasonTitle("  "));
    }
}
