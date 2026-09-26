package org.hlopes.aiinfusion.services;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.SessionScoped;

@RegisterAiService
@SessionScoped
public interface AIAssistant {

    Multi<String> chat(String userMessage);
}
