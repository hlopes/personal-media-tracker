package org.hlopes.resource;

import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.hlopes.dto.AddToLibraryRequest;
import org.hlopes.dto.LibraryEntryResponse;
import org.hlopes.dto.PaginatedLibraryResponse;
import org.hlopes.dto.UpdateLibraryRequest;
import org.hlopes.service.LibraryService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/me/library")
@Tag(name = "Library", description = "Personal Wishlist — Library Entry management")
public class LibraryResource {

    @Inject
    LibraryService libraryService;

    @Inject
    JsonWebToken jwt;

    @POST
    @RolesAllowed("User")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response add(@Valid AddToLibraryRequest request) {
        String email = jwt.getSubject();
        LibraryEntryResponse created = libraryService.add(
                email, request.externalId(), request.mediaType(), request.status(), request.rating());

        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PATCH
    @Path("/library/{id}")
    @RolesAllowed("User")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LibraryEntryResponse update(@PathParam("id") java.util.UUID id, UpdateLibraryRequest request) {
        String email = jwt.getSubject();

        if (request == null) {
            throw new jakarta.ws.rs.WebApplicationException(
                    jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.BAD_REQUEST)
                            .entity(java.util.Map.of("error", "status or rating must be provided"))
                            .build());
        }
        return libraryService.update(email, id, request.status(), request.rating());
    }

    @GET
    @RolesAllowed("User")
    @Produces(MediaType.APPLICATION_JSON)
    public PaginatedLibraryResponse list(
            @QueryParam("status") @DefaultValue("WISHLIST") String status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        String email = jwt.getSubject();
        List<LibraryEntryResponse> entries = libraryService.list(email, status, page, size);
        long total = libraryService.count(email, status);

        return new PaginatedLibraryResponse(entries, page, size, total);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("User")
    public Response remove(@PathParam("id") java.util.UUID id) {
        String email = jwt.getSubject();
        libraryService.remove(email, id);

        return Response.noContent().build();
    }
}
