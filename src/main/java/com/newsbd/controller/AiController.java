package com.newsbd.controller;

import com.newsbd.service.AiService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /**
     * POST /api/ai/summarize
     * Body: { "text": "...", "length": "short|medium|bullets" }
     */
    @PostMapping("/summarize")
    public ResponseEntity<Map<String, String>> summarize(@RequestBody SummarizeRequest req) {
        if (req.getText() == null || req.getText().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Text is required"));
        }
        String length = req.getLength() != null ? req.getLength() : "short";
        String summary = aiService.summarize(req.getText(), length);
        return ResponseEntity.ok(Map.of("summary", summary));
    }

    /**
     * POST /api/ai/translate
     * Body: { "text": "...", "targetLang": "bn|en" }
     * Translates between English and Bangla
     */
    @PostMapping("/translate")
    public ResponseEntity<Map<String, String>> translate(@RequestBody TranslateRequest req) {
        if (req.getText() == null || req.getText().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Text is required"));
        }
        String targetLang = req.getTargetLang() != null ? req.getTargetLang() : "bn";
        String translated = aiService.translate(req.getText(), targetLang);
        return ResponseEntity.ok(Map.of(
            "translatedText", translated,
            "targetLang", targetLang
        ));
    }

    @Data
    static class SummarizeRequest {
        private String text;
        private String length;
    }

    @Data
    static class TranslateRequest {
        private String text;
        private String targetLang;
    }
}
