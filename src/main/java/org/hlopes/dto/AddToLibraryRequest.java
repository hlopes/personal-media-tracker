package org.hlopes.dto;

import jakarta.validation.constraints.NotNull;

public record AddToLibraryRequest(@NotNull Long externalId, @NotNull String mediaType) {}
