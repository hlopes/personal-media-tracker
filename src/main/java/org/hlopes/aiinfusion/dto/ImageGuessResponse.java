package org.hlopes.aiinfusion.dto;

public record ImageGuessResponse(
        String guessTitle,
        String year,
        String mediaType,
        String confidence,
        Long catalogId,
        String catalogMediaType,
        String posterUrl,
        String detailUrl,
        String message,
        String wishlistStatus,
        boolean suggestAdd) {}
