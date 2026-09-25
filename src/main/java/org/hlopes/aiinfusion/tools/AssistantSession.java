package org.hlopes.aiinfusion.tools;

/**
 * Chat memory id of the Assistant: one per WebSocket connection, carrying the authenticated User's email taken from
 * the JWT. Tools read the User from here, never from model-supplied arguments (ADR 0007).
 */
public record AssistantSession(String connectionId, String email) {}
