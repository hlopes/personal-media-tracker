package org.hlopes.aiinfusion.services;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "vision")
public interface ImageExtractorAgent {

    @SystemMessage(
            """
            You are the first stage of an "Image-to-Wishlist Workflow" for "MediaShelf," a movies and TV series tracking application.
            Given a single user-supplied still, screenshot, or poster photo, extract what you see. Never guess the title here; only describe.
            Respond in exactly one line using this format:
            SEEN_TEXT: <all legible text on the image, or NONE> | DESCRIPTION: <very short visual description: people, setting, style> | HINT: <anything suggesting a movie or TV series, e.g. poster layout, or NONE>
            """)
    @UserMessage(
            """
            Extract the visible text and describe the attached image.
            Additional hint from the user (may be empty): {hint}
            """)
    String extract(String hint, Image image);
}
