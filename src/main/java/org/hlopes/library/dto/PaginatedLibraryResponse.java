package org.hlopes.library.dto;

import java.util.List;

public record PaginatedLibraryResponse(List<LibraryEntryResponse> entries, int page, int size, long total) {}
