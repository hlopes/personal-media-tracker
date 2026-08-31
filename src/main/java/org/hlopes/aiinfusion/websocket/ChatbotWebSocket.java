package org.hlopes.aiinfusion.websocket;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hlopes.aiinfusion.services.AIAssistant;

import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.RunOnVirtualThread;
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
    @RunOnVirtualThread
    public String onMessage(String userMessage, WebSocketConnection connection) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return "Please login to use the chatbot.";
        }

        if (userMessage == null || userMessage.isBlank()) {
            return "Please send a message.";
        }

        String trimmed = userMessage.trim();

        if (trimmed.length() > 2000) {
            trimmed = trimmed.substring(0, 2000);
        }

        Log.debugf("Chatbot WS message from %s (%s): %s", connection.id(), jwt.getSubject(), trimmed);

        try {
            String reply = aiAssistant.chat(trimmed);

            return reply != null ? reply : "Sorry, I could not generate a response.";
        } catch (Exception e) {
            Log.errorf(e, "Chatbot WS error for %s", connection.id());

            return "Sorry, I encountered an error. Please try again.";
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        Log.debugf("Chatbot WS closed: %s", connection.id());
    }
}
