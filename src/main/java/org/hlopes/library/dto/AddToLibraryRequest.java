package org.hlopes.library.dto;

import jakarta.validation.constraints.NotNull;

public record AddToLibraryRequest(@NotNull Long externalId, @NotNull String mediaType, String status, Short rating) {
    public AddToLibraryRequest(@NotNull Long externalId, @NotNull String mediaType) {
        this(externalId, mediaType, null, null);
    }
}
