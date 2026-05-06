package com.newsbd.repository;

import com.newsbd.model.Article;
import com.newsbd.model.Article.Section;
import com.newsbd.model.Article.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    boolean existsByUrlHash(String urlHash);

    Optional<Article> findByIdAndIsDeletedFalse(Long id);

    // ── Main feed — mixed from ALL sources, sorted by date ──
    @Query("""
        SELECT a FROM Article a
        WHERE a.isDeleted = false
          AND (:section IS NULL OR a.section = :section)
          AND (:category IS NULL OR a.category = :category)
          AND (:from IS NULL OR a.publishedAt >= :from)
          AND (:to   IS NULL OR a.publishedAt <= :to)
        ORDER BY a.publishedAt DESC
    """)
    Page<Article> findByFilters(
            @Param("section")   Section section,
            @Param("category")  Category category,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to,
            Pageable pageable
    );

    // ── Mixed feed — round robin from different sources ────
    @Query(value = """
        SELECT * FROM (
            SELECT a.*, ROW_NUMBER() OVER (
                PARTITION BY a.source_name
                ORDER BY a.published_at DESC
            ) as rn
            FROM articles a
            WHERE a.is_deleted = false
              AND (:section IS NULL OR a.section = :section)
        ) ranked
        WHERE rn <= 3
        ORDER BY published_at DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<Article> findMixedFromAllSources(
            @Param("section") String section,
            @Param("limit")   int limit
    );

    // ── Search ─────────────────────────────────────────────
    @Query(value = """
        SELECT * FROM articles
        WHERE is_deleted = false
          AND (:section IS NULL OR section = :section)
          AND title LIKE CONCAT('%', :q, '%')
        ORDER BY published_at DESC
        LIMIT 20
    """, nativeQuery = true)
    List<Article> searchByTitle(
            @Param("q")       String query,
            @Param("section") String section
    );

    // ── Trending ───────────────────────────────────────────
    List<Article> findByIsTrendingTrueAndIsDeletedFalseAndSectionOrderByViewCountDesc(
            Section section, Pageable pageable
    );

    // ── Breaking ───────────────────────────────────────────
    List<Article> findByIsBreakingTrueAndIsDeletedFalseOrderByPublishedAtDesc();

    // ── Admin ──────────────────────────────────────────────
    Page<Article> findByIsDeletedFalseOrderByPublishedAtDesc(Pageable pageable);

    // ── Stats ──────────────────────────────────────────────
    long countByIsDeletedFalse();
    long countByIsTrendingTrueAndIsDeletedFalse();

    // ── View count ─────────────────────────────────────────
    @Modifying
    @Query("UPDATE Article a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
    void incrementViewCount(@Param("id") Long id);
}