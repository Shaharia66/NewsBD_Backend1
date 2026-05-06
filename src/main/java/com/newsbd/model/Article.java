package com.newsbd.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "articles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_hash", nullable = false, unique = true, length = 64)
    private String urlHash;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 2000)
    private String url;

    @Column(name = "url_to_image", length = 2000)
    private String urlToImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private NewsSource source;

    @Column(name = "source_name")
    private String sourceName;

    private String author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Section section;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "fetched_at", updatable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;

    @Column(name = "is_trending")
    @Builder.Default
    private Boolean isTrending = false;

    @Column(name = "is_breaking")
    @Builder.Default
    private Boolean isBreaking = false;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    public enum Section { BANGLADESH, INTERNATIONAL }

    public enum Category { LOCAL, INTERNATIONAL, TECH, SPORTS, POLITICS, BUSINESS, HEALTH, ENTERTAINMENT }
}
