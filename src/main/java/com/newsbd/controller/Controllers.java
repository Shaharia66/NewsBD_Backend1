package com.newsbd.controller;

import com.newsbd.model.*;
import com.newsbd.repository.*;
import com.newsbd.service.NewsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// ===================================================================
// AUTH CONTROLLER
// ===================================================================
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getProfile(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(Map.of(
            "id",         user.getId(),
            "email",      user.getEmail(),
            "name",       user.getName() != null ? user.getName() : "",
            "pictureUrl", user.getPictureUrl() != null ? user.getPictureUrl() : "",
            "role",       user.getRole().name()
        ));
    }
}

// ===================================================================
// BOOKMARK CONTROLLER
// ===================================================================
@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
class BookmarkController {

    private final BookmarkRepository bookmarkRepository;
    private final ArticleRepository  articleRepository;
    private final UserRepository     userRepository;

    @GetMapping
    public ResponseEntity<List<Article>> getBookmarks(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        List<Bookmark> bookmarks = bookmarkRepository.findByUserIdWithArticles(user.getId());
        List<Article> articles = bookmarks.stream().map(Bookmark::getArticle).toList();
        return ResponseEntity.ok(articles);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addBookmark(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestBody BookmarkRequest req
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        if (bookmarkRepository.existsByUserIdAndArticleId(user.getId(), req.getArticleId())) {
            return ResponseEntity.ok(Map.of("message", "Already bookmarked"));
        }
        Article article = articleRepository.findById(req.getArticleId()).orElseThrow();
        Bookmark bookmark = Bookmark.builder().user(user).article(article).build();
        bookmarkRepository.save(bookmark);
        return ResponseEntity.ok(Map.of("message", "Bookmarked successfully"));
    }

    @DeleteMapping("/{articleId}")
    public ResponseEntity<Void> removeBookmark(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable Long articleId
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        bookmarkRepository.deleteByUserIdAndArticleId(user.getId(), articleId);
        return ResponseEntity.noContent().build();
    }

    @Data
    static class BookmarkRequest { private Long articleId; }
}

// ===================================================================
// ADMIN CONTROLLER
// ===================================================================
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
class AdminController {

    private final NewsService        newsService;
    private final NewsSourceRepository sourceRepository;
    private final ArticleRepository  articleRepository;
    private final UserRepository     userRepository;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
            "totalArticles",  newsService.countAll(),
            "activeSources",  sourceRepository.countByIsBlockedFalse(),
            "dailyVisitors",  82000L,  // plug in real analytics
            "aiSummaries",    3420L,   // plug in real counter
            "trendingArticles", newsService.countTrending()
        ));
    }

    // ---- SOURCES ----
    @GetMapping("/sources")
    public ResponseEntity<List<NewsSource>> getSources() {
        return ResponseEntity.ok(sourceRepository.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping("/sources")
    public ResponseEntity<NewsSource> addSource(@RequestBody NewsSource source) {
        return ResponseEntity.ok(sourceRepository.save(source));
    }

    @PatchMapping("/sources/{id}")
    public ResponseEntity<NewsSource> toggleSource(
        @PathVariable Long id, @RequestBody Map<String, Boolean> body
    ) {
        NewsSource source = sourceRepository.findById(id).orElseThrow();
        if (body.containsKey("blocked")) source.setIsBlocked(body.get("blocked"));
        if (body.containsKey("trusted")) source.setIsTrusted(body.get("trusted"));
        return ResponseEntity.ok(sourceRepository.save(source));
    }

    @DeleteMapping("/sources/{id}")
    public ResponseEntity<Void> deleteSource(@PathVariable Long id) {
        sourceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- ARTICLES ----
    @GetMapping("/articles")
    public ResponseEntity<Page<Article>> getArticles(
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(newsService.getAllForAdmin(page, size));
    }

    @PatchMapping("/articles/{id}/trending")
    public ResponseEntity<Article> markTrending(
        @PathVariable Long id, @RequestBody Map<String, Boolean> body
    ) {
        return ResponseEntity.ok(newsService.markTrending(id, body.getOrDefault("trending", false)));
    }

    @PatchMapping("/articles/{id}/breaking")
    public ResponseEntity<Article> markBreaking(
        @PathVariable Long id, @RequestBody Map<String, Boolean> body
    ) {
        return ResponseEntity.ok(newsService.markBreaking(id, body.getOrDefault("breaking", false)));
    }

    @DeleteMapping("/articles/{id}")
    public ResponseEntity<Void> deleteArticle(@PathVariable Long id) {
        newsService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- ANALYTICS ----
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        return ResponseEntity.ok(Map.of(
            "categoryBreakdown", Map.of(
                "POLITICS", 85, "BUSINESS", 72, "SPORTS", 68,
                "TECH", 60, "HEALTH", 42, "ENTERTAINMENT", 38
            ),
            "dailyViews", List.of(32000, 45000, 38000, 61000, 55000, 72000, 82000)
        ));
    }
}
