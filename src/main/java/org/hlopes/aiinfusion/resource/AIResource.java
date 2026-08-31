package org.hlopes.aiinfusion.resource;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.hlopes.aiinfusion.services.AIAssistant;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/api/ai")
@Tag(name = "AI Infusion", description = "Endpoints for AI Infusion")
public class AIResource {

    @Inject
    AIAssistant aiAssistant;

    @GET
    @Path("/simple-call")
    @PermitAll
    public Response getChatResponse(@QueryParam("userMessage") String userMessage) {
        return Response.ok().entity(aiAssistant.chat(userMessage)).build();
    }
}
