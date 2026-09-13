package org.hlopes.aiinfusion.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.hlopes.aiinfusion.dto.TavilySearchResponse;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class TavilySearchToolTest {

    @Inject
    TavilySearchTool tavilySearchTool;

    @Test
    public void testBlankApiKeyReturnsUnavailableWithoutNetwork() {
        assertEquals(TavilySearchTool.UNAVAILABLE, tavilySearchTool.tavilySearch("The Matrix 1999 movie"));
    }

    @Test
    public void testBlankQueryReturnsUnavailable() {
        assertEquals(TavilySearchTool.UNAVAILABLE, tavilySearchTool.tavilySearch("   "));
    }

    @Test
    public void testFormatResultsEmpty() {
        assertEquals("no web results", TavilySearchTool.formatResults(new TavilySearchResponse("q", List.of())));
        assertEquals("no web results", TavilySearchTool.formatResults(null));
    }

    @Test
    public void testFormatResultsTruncatesLongSnippets() {
        String longContent = "x".repeat(2000);
        var response = new TavilySearchResponse(
                "matrix",
                List.of(new TavilySearchResponse.Result("The Matrix", "https://example.com/matrix", longContent, 0.9)));

        String formatted = TavilySearchTool.formatResults(response);

        assertTrue(formatted.contains("The Matrix"));
        assertTrue(formatted.contains("https://example.com/matrix"));
        assertTrue(formatted.length() <= TavilySearchTool.MAX_TOTAL);
    }
}
