package org.hlopes.dto;

import jakarta.validation.constraints.NotNull;

public record AddToLibraryRequest(@NotNull Long externalId, @NotNull String mediaType, String status, Integer rating) {
    public AddToLibraryRequest(@NotNull Long externalId, @NotNull String mediaType) {
        this(externalId, mediaType, null, null);
    }
}
