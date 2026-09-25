package org.hlopes.aiinfusion.resource;

import java.nio.file.Files;
import java.util.Map;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.hlopes.aiinfusion.dto.ImageIdentificationResponse;
import org.hlopes.aiinfusion.services.ImageIdentificationService;
import org.hlopes.config.ApplicationConfig;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/chat/image")
@Tag(name = "Chat", description = "Image Identification from Copilot chat (vision :9001)")
public class ImageIdentificationResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    ImageIdentificationService imageIdentificationService;

    @POST
    @RolesAllowed("User")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public ImageIdentificationResponse identify(@RestForm("image") FileUpload image) {
        if (image == null || image.uploadedFile() == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "image is required"))
                    .build());
        }

        try {
            byte[] bytes = Files.readAllBytes(image.uploadedFile());

            return imageIdentificationService.identify(jwt.getSubject(), bytes, image.contentType());
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "could not read uploaded image"))
                    .build());
        }
    }
}
