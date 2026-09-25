package org.hlopes.aiinfusion.dto;

public record ImageIdentificationResponse(
        boolean identified,
        String title,
        Integer year,
        String mediaType,
        String description,
        String catalogType,
        Long catalogId,
        String message) {}
