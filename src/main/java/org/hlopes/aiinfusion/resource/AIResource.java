package org.hlopes.aiinfusion.resource;

import java.nio.file.Files;
import java.util.Map;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.hlopes.aiinfusion.dto.ImageGuessResponse;
import org.hlopes.aiinfusion.services.AIAssistant;
import org.hlopes.aiinfusion.services.ImageToWishlistSequence;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/ai")
@Tag(name = "AI Infusion", description = "Endpoints for AI Infusion")
public class AIResource {

    private final String ERROR = "error";

    @Inject
    JsonWebToken jwt;

    @Inject
    AIAssistant aiAssistant;

    @Inject
    ImageToWishlistSequence imageToWishlistSequence;

    @GET
    @Path("/simple-call")
    @PermitAll
    public Response getChatResponse(@QueryParam("userMessage") String userMessage) {
        return Response.ok().entity(aiAssistant.chat(userMessage)).build();
    }

    @POST
    @Path("/guess-image")
    @RolesAllowed("User")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response guessImage(@RestForm("image") FileUpload image, @RestForm("prompt") String prompt) {
        if (image == null || image.filePath() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ERROR, "image is required"))
                    .build();
        }

        try {
            byte[] bytes = Files.readAllBytes(image.filePath());
            String contentType = image.contentType();
            String fileName = image.fileName();
            ImageGuessResponse result =
                    imageToWishlistSequence.guess(bytes, contentType, fileName, prompt, jwt.getSubject());

            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "invalid image" : e.getMessage();

            if (message.contains("at most 5MB")) {
                return Response.status(413).entity(Map.of(ERROR, message)).build();
            }

            if (message.contains("must be one of")) {
                return Response.status(415).entity(Map.of(ERROR, message)).build();
            }

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ERROR, message))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(ERROR, "guess failed"))
                    .build();
        }
    }
}
