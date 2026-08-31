package org.hlopes.aiinfusion.services;

import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface AIAssistant {

    String chat(String userMessage);
}
