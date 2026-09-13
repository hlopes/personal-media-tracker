package org.hlopes.aiinfusion.tools;

import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hlopes.aiinfusion.client.TavilyClient;
import org.hlopes.aiinfusion.dto.TavilySearchRequest;
import org.hlopes.aiinfusion.dto.TavilySearchResponse;
import org.hlopes.config.ApplicationConfig;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TavilySearchTool {

    static final String UNAVAILABLE = "web search unavailable";

    static final int MAX_SNIPPET = 500;

    static final int MAX_TOTAL = 3000;

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    @RestClient
    TavilyClient tavilyClient;

    @Tool(
            "Search the web to identify a movie or TV series from poster text or a visual description. Input is a short search query. Returns up to 5 web results with title, url and snippet.")
    public String tavilySearch(String query) {
        String apiKey = applicationConfig.tavily().apiKey().orElse("");

        if (apiKey.isBlank()) {
            return UNAVAILABLE;
        }

        String effectiveQuery = query == null ? "" : query.trim();

        if (effectiveQuery.isBlank()) {
            return UNAVAILABLE;
        }

        try {
            List<String> domains = Arrays.stream(
                            applicationConfig.tavily().includeDomains().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
            TavilySearchRequest request = new TavilySearchRequest(
                    apiKey,
                    effectiveQuery,
                    applicationConfig.tavily().searchDepth(),
                    applicationConfig.tavily().maxResults(),
                    domains);

            return formatResults(tavilyClient.search(request));
        } catch (Exception e) {
            Log.warnf("Tavily search failed for query=%s: %s", effectiveQuery, e.getMessage());

            return "web search failed";
        }
    }

    public static String formatResults(TavilySearchResponse response) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return "no web results";
        }

        StringBuilder out = new StringBuilder();
        int index = 1;

        for (TavilySearchResponse.Result result : response.results()) {
            String title = result.title() == null ? "" : result.title().trim();
            String url = result.url() == null ? "" : result.url().trim();
            String snippet = result.content() == null ? "" : result.content().trim();

            if (snippet.length() > MAX_SNIPPET) {
                snippet = snippet.substring(0, MAX_SNIPPET);
            }

            out.append(index)
                    .append(". ")
                    .append(title)
                    .append("\n")
                    .append(url)
                    .append("\n")
                    .append(snippet)
                    .append("\n\n");
            index = index + 1;

            if (out.length() >= MAX_TOTAL) {
                break;
            }
        }

        String formatted = out.toString().trim();

        if (formatted.length() > MAX_TOTAL) {
            return formatted.substring(0, MAX_TOTAL);
        }

        return formatted;
    }
}
