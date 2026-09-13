package org.hlopes.aiinfusion.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.hlopes.aiinfusion.dto.TavilySearchRequest;
import org.hlopes.aiinfusion.dto.TavilySearchResponse;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@RegisterRestClient(configKey = "tavily")
public interface TavilyClient {

    @POST
    @Path("/search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    TavilySearchResponse search(TavilySearchRequest request);
}
