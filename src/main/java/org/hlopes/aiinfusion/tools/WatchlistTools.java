package org.hlopes.aiinfusion.tools;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.hlopes.catalog.dto.CatalogSearchResult;
import org.hlopes.catalog.dto.MediaItemDto;
import org.hlopes.catalog.service.CatalogService;
import org.hlopes.library.entity.StatusEnum;
import org.hlopes.library.service.LibraryService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class WatchlistTools {

    private static final int MAX_CANDIDATES = 5;
    private static final String UNKNOWN_YEAR = "unknown";

    @Inject
    CatalogService catalogService;

    @Inject
    LibraryService libraryService;

    @Tool("Search the movie and TV catalog by title. Returns up to 5 candidates, one per line, with externalId, "
            + "mediaType, title and year, or NOT_FOUND. Always call this before addToWatchlist.")
    public String searchCatalog(
            @P("title to search for") String query,
            @P(value = "movie or tv; leave empty if unknown", required = false) String mediaType) {
        String type = "movie".equalsIgnoreCase(mediaType) || "tv".equalsIgnoreCase(mediaType)
                ? mediaType.toLowerCase()
                : "multi";

        try {
            List<CatalogSearchResult> results =
                    catalogService.search(query, type, 1).results();

            if (results.isEmpty()) {
                return "NOT_FOUND";
            }

            return results.stream()
                    .limit(MAX_CANDIDATES)
                    .map(r -> "externalId=" + r.externalId()
                            + " | mediaType=" + ("TV_SERIES".equals(r.mediaType()) ? "tv" : "movie")
                            + " | title=" + r.title()
                            + " | year=" + yearOf(r.releaseDate()))
                    .collect(Collectors.joining("\n"));
        } catch (WebApplicationException e) {
            return "ERROR: " + errorOf(e);
        } catch (RuntimeException e) {
            Log.warnf(e, "Assistant searchCatalog failed for '%s'", query);

            return "ERROR: unexpected failure";
        }
    }

    @Tool("Add a catalog item to the current user's watchlist. externalId and mediaType must come from searchCatalog.")
    public String addToWatchlist(
            @ToolMemoryId Object session,
            @P("externalId returned by searchCatalog") long externalId,
            @P("movie or tv, as returned by searchCatalog") String mediaType) {
        if (!(session instanceof AssistantSession s)
                || s.email() == null
                || s.email().isBlank()) {
            return "ERROR: not authenticated";
        }

        String email = s.email();

        try {
            MediaItemDto item = catalogService.detail(mediaType, externalId).mediaItem();
            Optional<StatusEnum> existing = libraryService.findStatus(email, item.id());

            if (existing.isPresent()) {
                return "ALREADY_IN_LIBRARY:" + existing.get().name() + ": " + item.title();
            }

            libraryService.add(email, externalId, mediaType);

            return "ADDED: " + item.title() + " (" + yearOf(item.releaseDate()) + ", " + mediaType + ")";
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()
                    && !"user not found".equals(errorOf(e))) {
                return "NOT_FOUND";
            }

            return "ERROR: " + errorOf(e);
        } catch (RuntimeException e) {
            Log.warnf(e, "Assistant addToWatchlist failed for %s/%d", mediaType, externalId);

            return "ERROR: unexpected failure";
        }
    }

    private static String yearOf(LocalDate date) {
        return date == null ? UNKNOWN_YEAR : String.valueOf(date.getYear());
    }

    private static String yearOf(String isoDate) {
        return isoDate == null || isoDate.length() < 4 ? UNKNOWN_YEAR : isoDate.substring(0, 4);
    }

    private static String errorOf(WebApplicationException e) {
        if (e.getResponse().getEntity() instanceof Map<?, ?> body && body.get("error") != null) {
            return String.valueOf(body.get("error"));
        }

        return "catalog unavailable";
    }
}
