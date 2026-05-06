package com.newsbd.dto;

import com.newsbd.model.Article;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

// ===== ARTICLE DTO =====
@Data @Builder @NoArgsConstructor @AllArgsConstructor
class ArticleDto {
    private Long id;
    private String title;
    private String description;
    private String content;
    private String url;
    private String urlToImage;
    private String sourceName;
    private String author;
    private String section;
    private String category;
    private LocalDateTime publishedAt;
    private Long viewCount;
    private Boolean isTrending;
    private Boolean isBreaking;

    public static ArticleDto from(Article a) {
        return ArticleDto.builder()
            .id(a.getId())
            .title(a.getTitle())
            .description(a.getDescription())
            .content(a.getContent())
            .url(a.getUrl())
            .urlToImage(a.getUrlToImage())
            .sourceName(a.getSourceName())
            .author(a.getAuthor())
            .section(a.getSection() != null ? a.getSection().name() : null)
            .category(a.getCategory() != null ? a.getCategory().name() : null)
            .publishedAt(a.getPublishedAt())
            .viewCount(a.getViewCount())
            .isTrending(a.getIsTrending())
            .isBreaking(a.getIsBreaking())
            .build();
    }
}

// ===== AI REQUEST/RESPONSE =====
@Data @NoArgsConstructor @AllArgsConstructor
class SummarizeRequest {
    private String text;
    private String length; // short | medium | bullets
}

@Data @AllArgsConstructor
class SummarizeResponse {
    private String summary;
}

@Data @NoArgsConstructor @AllArgsConstructor
class TranslateRequest {
    private String text;
    private String targetLang; // bn | en
}

@Data @AllArgsConstructor
class TranslateResponse {
    private String translatedText;
    private String targetLang;
}

// ===== AUTH =====
@Data @AllArgsConstructor
class UserDto {
    private Long id;
    private String email;
    private String name;
    private String pictureUrl;
    private String role;
}

@Data @AllArgsConstructor
class AuthResponse {
    private String token;
    private UserDto user;
}

// ===== BOOKMARK =====
@Data @NoArgsConstructor @AllArgsConstructor
class BookmarkRequest {
    private Long articleId;
}

// ===== ADMIN STATS =====
@Data @Builder @AllArgsConstructor @NoArgsConstructor
class AdminStatsDto {
    private long totalArticles;
    private long activeSources;
    private long dailyVisitors;
    private long aiSummaries;
    private long trendingArticles;
}

// ===== SOURCE DTO =====
@Data @Builder @AllArgsConstructor @NoArgsConstructor
class NewsSourceDto {
    private Long id;
    private String name;
    private String apiEndpoint;
    private String apiType;
    private String section;
    private Boolean isTrusted;
    private Boolean isBlocked;
    private Long articleCount;
}

// Export all as public — package-private inner classes exposed via service layer
public class Dtos {
    public static ArticleDto     article(Article a)    { return ArticleDto.from(a); }
    public static List<ArticleDto> articles(List<Article> list) { return list.stream().map(ArticleDto::from).toList(); }

    // Re-export types
    public static final Class<SummarizeRequest>  SUMMARIZE_REQUEST  = SummarizeRequest.class;
    public static final Class<TranslateRequest>  TRANSLATE_REQUEST  = TranslateRequest.class;
    public static final Class<BookmarkRequest>   BOOKMARK_REQUEST   = BookmarkRequest.class;
    public static final Class<NewsSourceDto>     SOURCE_DTO         = NewsSourceDto.class;
}
