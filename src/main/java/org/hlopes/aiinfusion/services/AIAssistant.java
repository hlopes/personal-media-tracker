package org.hlopes.aiinfusion.services;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

@RegisterAiService
public interface AIAssistant {

    String chat(String userMessage);

    Multi<String> streamChat(String userMessage);
}
