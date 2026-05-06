package com.newsbd.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiService {

    private final WebClient webClient;

    @Value("${app.groq.api-key}")
    private String groqApiKey;

    public AiService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    // ══════════════════════════════════════════════════════
    //  SUMMARIZE — auto detects language of article
    // ══════════════════════════════════════════════════════
    public String summarize(String text, String length) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Text cannot be empty");
        if (text.length() > 15000) text = text.substring(0, 15000);
        if (length == null || length.isBlank()) length = "short";

        // Detect if text is Bangla (contains Bengali unicode characters)
        boolean isBangla = isBanglaText(text);
        String outputLanguage = isBangla ? "Bengali (Bangla)" : "English";

        log.info("Summarizing — length: {}, chars: {}, detected language: {}", length, text.length(), outputLanguage);
        String prompt = buildSummaryPrompt(text, length, outputLanguage);
        return callGroq(prompt, 600);
    }

    // ══════════════════════════════════════════════════════
    //  TRANSLATE
    // ══════════════════════════════════════════════════════
    public String translate(String text, String targetLang) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Text cannot be empty");
        if (text.length() > 10000) text = text.substring(0, 10000);

        String targetLanguage = "bn".equalsIgnoreCase(targetLang) ? "Bengali (Bangla)" : "English";
        log.info("Translating to {} — chars: {}", targetLanguage, text.length());

        String prompt = "You are a professional translator specializing in Bengali and English. "
                + "Translate the following text accurately and naturally into " + targetLanguage + ". "
                + "Preserve the original formatting and paragraph structure. "
                + "Use natural, fluent language — not word-for-word literal translation. "
                + "Keep proper nouns (names, places, organizations) in their standard form. "
                + "Return ONLY the translated text, nothing else.\n\n"
                + "Text to translate:\n" + text;

        return callGroq(prompt, 2048);
    }

    // ══════════════════════════════════════════════════════
    //  DETECT BANGLA TEXT
    // ══════════════════════════════════════════════════════
    private boolean isBanglaText(String text) {
        if (text == null) return false;
        long banglaChars = text.chars()
                .filter(c -> c >= 0x0980 && c <= 0x09FF) // Bengali Unicode block
                .count();
        // If more than 10% of characters are Bangla, treat as Bangla text
        return banglaChars > (text.length() * 0.1);
    }

    // ══════════════════════════════════════════════════════
    //  PROMPT BUILDER — with language awareness
    // ══════════════════════════════════════════════════════
    private String buildSummaryPrompt(String text, String length, String outputLanguage) {
        String langInstruction = "Write the summary in " + outputLanguage + " only.";

        switch (length) {
            case "medium":
                return "You are a professional news editor. "
                        + "Write a clear, balanced summary of this article in one paragraph of 4-5 sentences. "
                        + "Cover the key facts: who, what, when, where, why. Be factual and neutral. "
                        + langInstruction + " "
                        + "Return ONLY the summary paragraph, nothing else.\n\n"
                        + "Article:\n" + text;

            case "bullets":
                return "You are a professional news editor. "
                        + "Summarize this article as exactly 5 bullet points. "
                        + "Each bullet should cover one key fact. Start each bullet with \"- \". "
                        + langInstruction + " "
                        + "Return ONLY the 5 bullet points, nothing else.\n\n"
                        + "Article:\n" + text;

            default: // short
                return "You are a professional news editor. "
                        + "Write a concise 2-3 sentence summary of this article. "
                        + "Focus only on the most important facts. Be clear and factual. "
                        + langInstruction + " "
                        + "Return ONLY the summary, nothing else.\n\n"
                        + "Article:\n" + text;
        }
    }

    // ══════════════════════════════════════════════════════
    //  CALL GROQ API
    // ══════════════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    private String callGroq(String prompt, int maxTokens) {
        if (groqApiKey == null || groqApiKey.isBlank() || groqApiKey.startsWith("YOUR_")) {
            throw new RuntimeException("Groq API key not configured in application.properties");
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    ),
                    "max_tokens", maxTokens,
                    "temperature", 0.3
            );

            Map<?, ?> response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) throw new RuntimeException("Null response from Groq API");

            if (response.get("choices") instanceof List<?> choices && !choices.isEmpty()) {
                Map<?, ?> choice  = (Map<?, ?>) choices.get(0);
                Map<?, ?> message = (Map<?, ?>) choice.get("message");
                String result = (String) message.get("content");
                if (result != null && !result.isBlank()) {
                    log.info("Groq success — result chars: {}", result.length());
                    return result.trim();
                }
            }
            throw new RuntimeException("Could not parse Groq API response");

        } catch (WebClientResponseException e) {
            log.error("Groq HTTP error: {} — {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Groq API error: " + e.getStatusCode() + " — " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Groq API call failed: {}", e.getMessage());
            throw new RuntimeException("AI service error: " + e.getMessage());
        }
    }
}