package org.hlopes.aiinfusion.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.hlopes.aiinfusion.dto.ImageIdentificationResponse;
import org.hlopes.catalog.dto.CatalogSearchResponse;
import org.hlopes.catalog.service.CatalogService;
import org.hlopes.config.ApplicationConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class ImageIdentificationService {

    static final long MAX_BYTES = 5L * 1024L * 1024L;
    static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    static final String NOT_IDENTIFIED = "I wasn't able to identify the movie/show from this image.";
    static final int LIMIT_PER_HOUR = 10;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    CatalogService catalogService;

    @Inject
    ChatModel chatModel;

    @Inject
    ObjectMapper objectMapper;

    private volatile OpenAiChatModel visionModel;

    private volatile String visionModelKey;

    public ImageIdentificationResponse identify(String email, byte[] bytes, String contentType) {
        checkRateLimit(email);

        if (bytes == null || bytes.length == 0) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "image is required"))
                    .build());
        }

        if (bytes.length > MAX_BYTES) {
            throw new WebApplicationException(Response.status(413)
                    .entity(Map.of("error", "image too large, max 5MB"))
                    .build());
        }

        String normalizedType = contentType == null
                ? ""
                : contentType.toLowerCase().split(";")[0].trim();

        if (!ALLOWED_TYPES.contains(normalizedType)) {
            throw new WebApplicationException(Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
                    .entity(Map.of("error", "unsupported image type, use jpg, png or webp"))
                    .build());
        }

        VisionResult vision = reasonTitle(observeImage(bytes, normalizedType));

        if (vision == null || !"high".equalsIgnoreCase(vision.confidence())) {
            return new ImageIdentificationResponse(false, null, null, null, null, null, null, NOT_IDENTIFIED);
        }

        String mediaType = normalizeMediaType(vision.mediaType());
        String catalogType = null;
        Long catalogId = null;

        try {
            CatalogSearchResponse search = catalogService.search(vision.title(), "multi", 1);

            if (search != null && search.results() != null && !search.results().isEmpty()) {
                var first = search.results().get(0);
                catalogType = first.mediaType();
                catalogId = first.externalId();
            }
        } catch (Exception ignored) {
        }

        return new ImageIdentificationResponse(
                true, vision.title(), vision.year(), mediaType, vision.description(), catalogType, catalogId, null);
    }

    void checkRateLimit(String email) {
        String key = email == null ? "anonymous" : email.toLowerCase();
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(1));
        Deque<Instant> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }

            if (deque.size() >= LIMIT_PER_HOUR) {
                throw new WebApplicationException(Response.status(429)
                        .entity(Map.of("error", "too many image identifications, try again later"))
                        .build());
            }

            deque.addLast(now);
        }
    }

    String observeImage(byte[] bytes, String contentType) {
        try {
            Image image = Image.builder()
                    .base64Data(Base64.getEncoder().encodeToString(bytes))
                    .mimeType(contentType)
                    .build();
            SystemMessage system = SystemMessage.from("You describe movie and TV posters, stills and screenshots. "
                    + "Transcribe any visible text exactly (titles, names, logos) and briefly describe what you see: "
                    + "people, clothing, objects, setting, style. Reply in plain sentences. Never invent text that "
                    + "is not visible; if no text is visible, say so.");
            UserMessage user = new UserMessage(
                    TextContent.from("Transcribe ALL text in this image letter by letter, including logos, small "
                            + "print and stylized titles. Then describe what movie or TV show is shown and list "
                            + "notable visual elements."),
                    ImageContent.from(image));

            ChatResponse response = visionModel().chat(List.of(system, user));
            String text = response.aiMessage().text();

            if (text == null || text.isBlank()) {
                return null;
            }

            return text.trim();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException(Response.status(502)
                    .entity(Map.of("error", "couldn't analyze this image right now, try again later"))
                    .build());
        }
    }

    VisionResult reasonTitle(String observation) {
        if (observation == null || observation.isBlank()) {
            return null;
        }

        try {
            SystemMessage system = SystemMessage.from("You identify movies and TV shows from a visual description. "
                    + "Given the OBSERVATION, decide the single most likely title. Return JSON only: "
                    + "{\"title\": string, \"year\": number|null, \"mediaType\": \"MOVIE\"|\"TV_SERIES\", "
                    + "\"confidence\": \"high\"|\"low\", \"description\": \"1-2 sentences\"}. "
                    + "Transcribed text may contain OCR errors, especially on stylized poster fonts. If the "
                    + "transcribed title points to a work that contradicts the visual description (wrong "
                    + "characters, costume, era, franchise markers), set confidence to low and prefer the "
                    + "visually supported answer. Use high only for unmistakable agreement between text and "
                    + "visuals. No markdown.");
            UserMessage user = UserMessage.from("OBSERVATION:\n" + observation);

            ChatResponse response = chatModel.chat(system, user);
            String text = response.aiMessage().text();

            if (text == null || text.isBlank()) {
                return null;
            }

            return parseVisionContent(text);
        } catch (Exception e) {
            throw new WebApplicationException(Response.status(502)
                    .entity(Map.of("error", "couldn't analyze this image right now, try again later"))
                    .build());
        }
    }

    VisionResult parseVisionContent(String raw) {
        try {
            String cleaned = raw == null ? "" : raw.trim();

            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf('{');
                int end = cleaned.lastIndexOf('}');

                if (start >= 0 && end > start) {
                    cleaned = cleaned.substring(start, end + 1);
                }
            }

            JsonNode node = objectMapper.readTree(cleaned);
            String title = node.path("title").asText(null);

            if (title == null || title.isBlank()) {
                return null;
            }

            Integer year = node.path("year").isNumber() ? node.path("year").asInt() : null;
            String mediaType = node.path("mediaType").asText(null);
            String confidence = node.path("confidence").asText("low");
            String description = node.path("description").asText(null);

            return new VisionResult(title.trim(), year, mediaType, confidence, description);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeMediaType(String raw) {
        if (raw == null) {
            return "MOVIE";
        }

        String v = raw.trim().toUpperCase();

        if (v.startsWith("TV")) {
            return "TV_SERIES";
        }

        return "MOVIE";
    }

    private OpenAiChatModel visionModel() {
        String key = applicationConfig.vision().baseUrl() + "|"
                + applicationConfig.vision().modelName();
        OpenAiChatModel cached = visionModel;

        if (cached != null && key.equals(visionModelKey)) {
            return cached;
        }

        synchronized (this) {
            cached = visionModel;

            if (cached != null && key.equals(visionModelKey)) {
                return cached;
            }

            OpenAiChatModel created = OpenAiChatModel.builder()
                    .baseUrl(applicationConfig.vision().baseUrl())
                    .apiKey("sk-local-dummy")
                    .modelName(applicationConfig.vision().modelName())
                    .temperature(0.0)
                    .maxTokens(500)
                    .timeout(applicationConfig.vision().timeout())
                    .build();
            visionModel = created;
            visionModelKey = key;

            return created;
        }
    }

    record VisionResult(String title, Integer year, String mediaType, String confidence, String description) {}
}
