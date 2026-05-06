package com.newsbd.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_sources")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NewsSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "api_endpoint", nullable = false)
    private String apiEndpoint;

    @Column(name = "api_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ApiType apiType;

    @Column(name = "api_key_ref")
    private String apiKeyRef;

    @Column(name = "section")
    @Enumerated(EnumType.STRING)
    private Article.Section section; // null means BOTH

    @Column(name = "is_trusted")
    @Builder.Default
    private Boolean isTrusted = true;

    @Column(name = "is_blocked")
    @Builder.Default
    private Boolean isBlocked = false;

    @Column(name = "article_count")
    @Builder.Default
    private Long articleCount = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum ApiType { NEWSAPI, GUARDIAN, RSS }
}
