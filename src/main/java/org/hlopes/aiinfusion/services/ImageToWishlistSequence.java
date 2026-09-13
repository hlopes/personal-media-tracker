package org.hlopes.aiinfusion.services;

import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.hlopes.aiinfusion.dto.ImageGuessResponse;
import org.hlopes.aiinfusion.util.ImageGuessParser;
import org.hlopes.auth.repository.UserRepository;
import org.hlopes.catalog.dto.CatalogSearchResult;
import org.hlopes.catalog.entity.MediaItem;
import org.hlopes.catalog.entity.MediaTypeEnum;
import org.hlopes.catalog.repository.MediaItemRepository;
import org.hlopes.catalog.service.CatalogService;
import org.hlopes.config.ApplicationConfig;
import org.hlopes.library.entity.LibraryEntry;
import org.hlopes.library.repository.LibraryEntryRepository;

import dev.langchain4j.data.image.Image;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ImageToWishlistSequence {

    static final long MAX_BYTES = 5L * 1024L * 1024L;

    static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    static final String FALLBACK_MESSAGE = "Sorry, I couldn't guess which movie this is from. Try a clearer still?";

    static final String ERROR_MESSAGE = "Sorry, I encountered an error. Please try again.";

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    ImageExtractorAgent imageExtractorAgent;

    @Inject
    MediaIdentifierAgent mediaIdentifierAgent;

    @Inject
    CatalogService catalogService;

    @Inject
    UserRepository userRepository;

    @Inject
    MediaItemRepository mediaItemRepository;

    @Inject
    LibraryEntryRepository libraryEntryRepository;

    public ImageGuessResponse guess(
            byte[] bytes, String contentType, String fileName, String prompt, String userEmail) {
        validateInput(bytes, contentType);
        String hint = prompt == null ? "" : prompt.trim();

        if (hint.length() > 500) {
            hint = hint.substring(0, 500);
        }

        Log.infof("Image-to-Wishlist request: file=%s size=%d type=%s", fileName, bytes.length, contentType);

        String extraction;

        try {
            extraction = imageExtractorAgent.extract(hint, toImage(bytes, contentType));
        } catch (Exception e) {
            Log.errorf(e, "Image-to-Wishlist extraction failed for file=%s", fileName);

            return fallback(ERROR_MESSAGE);
        }

        if (extraction == null || extraction.isBlank()) {
            return fallback(FALLBACK_MESSAGE);
        }

        String raw;

        try {
            raw = mediaIdentifierAgent.identify(extraction);
        } catch (Exception e) {
            Log.errorf(e, "Image-to-Wishlist identification failed for file=%s", fileName);

            return fallback(ERROR_MESSAGE);
        }

        if (raw == null || raw.isBlank()) {
            return fallback(FALLBACK_MESSAGE);
        }

        ImageGuessParser.ParsedGuess parsed = ImageGuessParser.parse(raw);

        if (!parsed.guessable()) {
            Log.infof("Image-to-Wishlist no confident match for file=%s raw=%s", fileName, raw);

            return fallback(FALLBACK_MESSAGE);
        }

        CatalogMatch match = resolveCatalog(parsed.title(), parsed.mediaType(), parsed.year());

        if (match.catalogId() == null) {
            return new ImageGuessResponse(
                    parsed.title(),
                    parsed.year(),
                    parsed.mediaType(),
                    parsed.confidence(),
                    null,
                    null,
                    null,
                    null,
                    "I think this is " + parsed.title() + " (" + parsed.year()
                            + "), but I couldn't find it in the catalog.",
                    "CATALOG_MISS",
                    false);
        }

        return withWishlistStatus(parsed, match, userEmail);
    }

    private ImageGuessResponse withWishlistStatus(
            ImageGuessParser.ParsedGuess parsed, CatalogMatch match, String userEmail) {
        MediaTypeEnum mediaType = toMediaType(match.catalogMediaType());
        LibraryEntry entry = findEntry(userEmail, match.catalogId(), mediaType);
        String title = parsed.title() + " (" + parsed.year() + ")";

        if (entry == null) {
            return new ImageGuessResponse(
                    parsed.title(),
                    parsed.year(),
                    parsed.mediaType(),
                    parsed.confidence(),
                    match.catalogId(),
                    match.catalogMediaType(),
                    match.posterUrl(),
                    match.detailUrl(),
                    "I found " + title + " — want me to add it to your wishlist?",
                    "NOT_IN_LIBRARY",
                    true);
        }

        if (entry.status == null) {
            return new ImageGuessResponse(
                    parsed.title(),
                    parsed.year(),
                    parsed.mediaType(),
                    parsed.confidence(),
                    match.catalogId(),
                    match.catalogMediaType(),
                    match.posterUrl(),
                    match.detailUrl(),
                    "Already in your library: " + title + ".",
                    "ALREADY_IN_LIBRARY",
                    false);
        }

        return switch (entry.status) {
            case WISHLIST -> new ImageGuessResponse(
                    parsed.title(),
                    parsed.year(),
                    parsed.mediaType(),
                    parsed.confidence(),
                    match.catalogId(),
                    match.catalogMediaType(),
                    match.posterUrl(),
                    match.detailUrl(),
                    "Already on your wishlist: " + title + ".",
                    "ALREADY_WISHLIST",
                    false);
            case COMPLETED -> new ImageGuessResponse(
                    parsed.title(),
                    parsed.year(),
                    parsed.mediaType(),
                    parsed.confidence(),
                    match.catalogId(),
                    match.catalogMediaType(),
                    match.posterUrl(),
                    match.detailUrl(),
                    "Already marked as Completed: " + title + ".",
                    "ALREADY_COMPLETED",
                    false);
            default -> new ImageGuessResponse(
                    parsed.title(),
                    parsed.year(),
                    parsed.mediaType(),
                    parsed.confidence(),
                    match.catalogId(),
                    match.catalogMediaType(),
                    match.posterUrl(),
                    match.detailUrl(),
                    "Already in your library as " + entry.status.name() + ": " + title + ".",
                    "ALREADY_" + entry.status.name(),
                    false);
        };
    }

    private LibraryEntry findEntry(String userEmail, Long catalogId, MediaTypeEnum mediaType) {
        String normalizedEmail = userEmail == null ? "" : userEmail.trim().toLowerCase();

        if (normalizedEmail.isBlank() || catalogId == null || mediaType == null) {
            return null;
        }

        var user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null) {
            return null;
        }

        MediaItem item = mediaItemRepository
                .findByExternalIdAndMediaType(catalogId, mediaType)
                .orElse(null);

        if (item == null) {
            return null;
        }

        return libraryEntryRepository
                .findByUserIdAndMediaItemId(user.id, item.id)
                .orElse(null);
    }

    private void validateInput(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("image is required");
        }

        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("image must be at most 5MB");
        }

        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("image must be one of jpeg, png, webp");
        }
    }

    private ImageGuessResponse fallback(String message) {
        return new ImageGuessResponse(
                "UNKNOWN", "?", "UNKNOWN", "LOW", null, null, null, null, message, "UNKNOWN", false);
    }

    private CatalogMatch resolveCatalog(String title, String mediaType, String year) {
        try {
            var response = catalogService.search(title, "multi", 1);

            if (response == null || response.results() == null) {
                return CatalogMatch.empty();
            }

            List<CatalogSearchResult> results = response.results();

            for (CatalogSearchResult result : results) {
                if (matches(result, title, mediaType, year)) {
                    return toMatch(result);
                }
            }

            if (!results.isEmpty()) {
                return toMatch(results.get(0));
            }

            return CatalogMatch.empty();
        } catch (Exception e) {
            Log.warnf("Image-to-Wishlist catalog resolve failed for title=%s: %s", title, e.getMessage());

            return CatalogMatch.empty();
        }
    }

    private boolean matches(CatalogSearchResult result, String title, String mediaType, String year) {
        if (result == null || result.title() == null) {
            return false;
        }

        boolean titleOk = result.title().equalsIgnoreCase(title)
                || result.title().toLowerCase().contains(title.toLowerCase())
                || title.toLowerCase().contains(result.title().toLowerCase());

        if (!titleOk) {
            return false;
        }

        if (mediaType != null
                && !mediaType.equals("UNKNOWN")
                && result.mediaType() != null
                && !result.mediaType().equalsIgnoreCase(mediaType)) {
            return false;
        }

        return year == null
                || year.equals("?")
                || result.releaseDate() == null
                || result.releaseDate().isBlank()
                || result.releaseDate().startsWith(year);
    }

    private CatalogMatch toMatch(CatalogSearchResult result) {
        String posterUrl = null;
        String detailUrl = null;

        try {
            if (result.posterPath() != null && !result.posterPath().isBlank()) {
                String imageBase = applicationConfig.tmdb().imageBaseUrl();
                posterUrl = imageBase + "/w185" + result.posterPath();
            }

            String slug = "movie";

            if ("TV_SERIES".equalsIgnoreCase(result.mediaType())) {
                slug = "tv";
            }

            if (result.externalId() != null) {
                detailUrl = "/api/media/" + slug + "/" + result.externalId();
            }
        } catch (Exception ignored) {
        }

        return new CatalogMatch(result.externalId(), result.mediaType(), posterUrl, detailUrl);
    }

    private MediaTypeEnum toMediaType(String catalogMediaType) {
        if (catalogMediaType == null || catalogMediaType.isBlank()) {
            return null;
        }

        try {
            return MediaTypeEnum.valueOf(catalogMediaType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Image toImage(byte[] bytes, String contentType) {
        String base64 = Base64.getEncoder().encodeToString(bytes);

        return Image.builder()
                .base64Data(base64)
                .mimeType(contentType.toLowerCase())
                .build();
    }

    private record CatalogMatch(Long catalogId, String catalogMediaType, String posterUrl, String detailUrl) {
        static CatalogMatch empty() {
            return new CatalogMatch(null, null, null, null);
        }
    }
}
