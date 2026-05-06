package com.newsbd.controller;

import com.newsbd.model.Article;
import com.newsbd.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    /**
     * GET /api/news?section=BANGLADESH&category=TECH&from=...&to=...&page=0&size=10
     */
    @GetMapping
    public ResponseEntity<Page<Article>> getArticles(
        @RequestParam(required = false) String section,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(newsService.getArticles(section, category, from, to, page, Math.min(size, 50)));
    }

    /**
     * GET /api/news/search?q=keyword&section=BANGLADESH
     */
    @GetMapping("/search")
    public ResponseEntity<List<Article>> search(
        @RequestParam String q,
        @RequestParam(required = false) String section
    ) {
        return ResponseEntity.ok(newsService.search(q, section));
    }

    /**
     * GET /api/news/trending?section=BANGLADESH
     */
    @GetMapping("/trending")
    public ResponseEntity<List<Article>> getTrending(
        @RequestParam(defaultValue = "BANGLADESH") String section
    ) {
        return ResponseEntity.ok(newsService.getTrending(section));
    }

    /**
     * GET /api/news/breaking
     */
    @GetMapping("/breaking")
    public ResponseEntity<List<Article>> getBreaking() {
        return ResponseEntity.ok(newsService.getBreaking());
    }

    /**
     * GET /api/news/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Article> getById(@PathVariable Long id) {
        return ResponseEntity.ok(newsService.getById(id));
    }

    /**
     * POST /api/news/{id}/view  — increment view counter (fire and forget)
     */
    @PostMapping("/{id}/view")
    public ResponseEntity<Void> incrementView(@PathVariable Long id) {
        newsService.incrementView(id);
        return ResponseEntity.noContent().build();
    }
}
