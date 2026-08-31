package org.hlopes.aiinfusion.websocket;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hlopes.aiinfusion.services.AIAssistant;

import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;

@WebSocket(path = "/chatbot")
public class ChatbotWebSocket {

    @Inject
    JsonWebToken jwt;

    @Inject
    AIAssistant aiAssistant;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        Log.debugf("Chatbot WS opened: %s", connection.id());
    }

    @OnTextMessage
    public Multi<String> onMessage(String userMessage, WebSocketConnection connection) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return Multi.createFrom().item("Please login to use the chatbot.");
        }

        if (userMessage == null || userMessage.isBlank()) {
            return Multi.createFrom().item("Please send a message.");
        }

        String trimmed = userMessage.trim();

        if (trimmed.length() > 2000) {
            trimmed = trimmed.substring(0, 2000);
        }

        Log.debugf("Chatbot WS message from %s (%s): %s", connection.id(), jwt.getSubject(), trimmed);

        return aiAssistant
                .streamChat(trimmed)
                .onFailure()
                .invoke(e -> Log.errorf(e, "Chatbot WS stream error for %s", connection.id()))
                .onFailure()
                .recoverWithItem("Sorry, I encountered an error. Please try again.");
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        Log.debugf("Chatbot WS closed: %s", connection.id());
    }
}
