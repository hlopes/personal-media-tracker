package org.hlopes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hlopes.aiinfusion.util.ImageGuessParser;
import org.junit.jupiter.api.Test;

public class ImageGuessParserTest {

    @Test
    public void testParseMovieGuess() {
        var parsed = ImageGuessParser.parse(
                "TITLE: Inception | YEAR: 2010 | TYPE: MOVIE | CONFIDENCE: HIGH | NOTE: Dream heist");

        assertEquals("Inception", parsed.title());
        assertEquals("2010", parsed.year());
        assertEquals("MOVIE", parsed.mediaType());
        assertEquals("HIGH", parsed.confidence());
        assertTrue(parsed.guessable());
    }

    @Test
    public void testParseUnknownFallback() {
        var parsed =
                ImageGuessParser.parse("TITLE: UNKNOWN | YEAR: ? | TYPE: UNKNOWN | CONFIDENCE: LOW | NOTE: Not sure");

        assertEquals("UNKNOWN", parsed.title());
        assertFalse(parsed.guessable());
    }

    @Test
    public void testParseEmptyIsNotGuessable() {
        var parsed = ImageGuessParser.parse("");

        assertFalse(parsed.guessable());
    }

    @Test
    public void testParseTvNormalizes() {
        var parsed = ImageGuessParser.parse(
                "TITLE: Breaking Bad | YEAR: 2008 | TYPE: TV | CONFIDENCE: MEDIUM | NOTE: Lab scene");

        assertEquals("TV_SERIES", parsed.mediaType());
        assertTrue(parsed.guessable());
    }

    @Test
    public void testParseLowConfidenceIsNotGuessable() {
        var parsed = ImageGuessParser.parse("TITLE: Dune | YEAR: 2021 | TYPE: MOVIE | CONFIDENCE: LOW | NOTE: Maybe");

        assertFalse(parsed.guessable());
    }
}
