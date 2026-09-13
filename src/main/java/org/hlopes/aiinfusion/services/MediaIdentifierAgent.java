package org.hlopes.aiinfusion.services;

import org.hlopes.aiinfusion.tools.TavilySearchTool;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

@RegisterAiService
public interface MediaIdentifierAgent {

    @SystemMessage(
            """
            You are the second stage of an "Image-to-Wishlist Workflow" for "MediaShelf," a movies and TV series tracking application.
            Given an image description, identify which Movie or TV Series it belongs to. Prefer the most likely well-known title.
            You may call the tavily_search tool at most twice to verify candidates via web search. Never invent Catalog IDs or URLs.
            If the description is not from a movie or TV series, or you cannot tell, say so.
            Always finish with exactly one line using this format:
            TITLE: <title or UNKNOWN> | YEAR: <4-digit year or ?> | TYPE: MOVIE or TV_SERIES or UNKNOWN | CONFIDENCE: HIGH or MEDIUM or LOW | NOTE: <very short note>
            """)
    @UserMessage(
            """
            Identify the Movie or TV Series described here, using web search when unsure:
            {extraction}
            If you cannot identify it with at least medium confidence, return TITLE: UNKNOWN with CONFIDENCE: LOW.
            """)
    @ToolBox(TavilySearchTool.class)
    String identify(String extraction);
}
