package org.hlopes.aiinfusion.util;

import java.util.HashMap;
import java.util.Map;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ImageGuessParser {

    public record ParsedGuess(
            String title, String year, String mediaType, String confidence, String note, boolean guessable) {}

    public static ParsedGuess parse(String raw) {
        Map<String, String> fields = extractFields(raw);
        String title = normalizeTitle(fields.get("TITLE"));
        String year = normalizeYear(fields.get("YEAR"));
        String mediaType = normalizeMediaType(fields.get("TYPE"));
        String confidence = normalizeConfidence(fields.get("CONFIDENCE"));
        String note = fields.getOrDefault("NOTE", "").trim();

        boolean guessable = isGuessable(title, mediaType, confidence);

        return new ParsedGuess(title, year, mediaType, confidence, note, guessable);
    }

    private static Map<String, String> extractFields(String raw) {
        Map<String, String> fields = new HashMap<>();

        if (raw == null || raw.isBlank()) {
            return fields;
        }

        String[] parts = raw.split("\\|");

        for (String part : parts) {
            int colon = part.indexOf(':');

            if (colon < 0) {
                continue;
            }

            String key = part.substring(0, colon).trim().toUpperCase();
            String value = part.substring(colon + 1).trim();

            if (!key.isBlank()) {
                fields.put(key, value);
            }
        }

        return fields;
    }

    private static String normalizeTitle(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("UNKNOWN") || value.equals("?")) {
            return "UNKNOWN";
        }

        return value.trim();
    }

    private static String normalizeYear(String value) {
        if (value == null || value.isBlank()) {
            return "?";
        }

        String trimmed = value.trim();

        if (trimmed.matches("\\d{4}")) {
            return trimmed;
        }

        return "?";
    }

    private static String normalizeMediaType(String value) {
        if (value == null) {
            return "UNKNOWN";
        }

        String upper = value.trim().toUpperCase();

        if (upper.equals("MOVIE") || upper.equals("TV_SERIES") || upper.equals("TV-SERIES") || upper.equals("TV")) {
            if (upper.startsWith("TV")) {
                return "TV_SERIES";
            }

            return "MOVIE";
        }

        return "UNKNOWN";
    }

    private static String normalizeConfidence(String value) {
        if (value == null) {
            return "LOW";
        }

        String upper = value.trim().toUpperCase();

        if (upper.equals("HIGH") || upper.equals("MEDIUM") || upper.equals("LOW")) {
            return upper;
        }

        return "LOW";
    }

    private static boolean isGuessable(String title, String mediaType, String confidence) {
        if (title.equals("UNKNOWN") || mediaType.equals("UNKNOWN")) {
            return false;
        }

        return confidence.equals("HIGH") || confidence.equals("MEDIUM");
    }
}
