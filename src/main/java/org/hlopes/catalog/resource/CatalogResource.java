package org.hlopes.catalog.resource;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.hlopes.catalog.dto.CatalogDetailResponse;
import org.hlopes.catalog.dto.CatalogSearchResponse;
import org.hlopes.catalog.service.CatalogService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/catalog")
@Tag(name = "Catalog", description = "Search TMDB catalog (proxied, auth required)")
public class CatalogResource {

    @Inject
    CatalogService catalogService;

    @GET
    @Path("/search")
    @RolesAllowed("User")
    @Produces(MediaType.APPLICATION_JSON)
    public CatalogSearchResponse search(
            @QueryParam("q") String q,
            @QueryParam("type") @DefaultValue("multi") String type,
            @QueryParam("page") @DefaultValue("1") int page) {
        if (q == null || q.isBlank()) {
            throw new BadRequestException("query param q is required");
        }

        return catalogService.search(q, type, page);
    }

    @GET
    @Path("/{type}/{id}")
    @RolesAllowed("User")
    @Produces(MediaType.APPLICATION_JSON)
    public CatalogDetailResponse detail(@PathParam("type") String type, @PathParam("id") Long id) {
        return catalogService.detail(type, id);
    }
}
