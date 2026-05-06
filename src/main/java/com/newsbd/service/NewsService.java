package com.newsbd.service;

import com.newsbd.model.Article;
import com.newsbd.model.Article.Category;
import com.newsbd.model.Article.Section;
import com.newsbd.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private final ArticleRepository articleRepository;

    // ══════════════════════════════════════════════════════
    //  MAIN FEED
    // ══════════════════════════════════════════════════════
    public Page<Article> getArticles(String section, String category,
                                     LocalDateTime from, LocalDateTime to,
                                     int page, int size) {
        try {
            Section sec = parseSection(section);
            Category cat = parseCategory(category);

            // If filtering by category or date — use normal query
            if (cat != null || from != null || to != null) {
                Pageable pageable = PageRequest.of(
                        Math.max(page, 0),
                        Math.min(size, 50),
                        Sort.by(Sort.Direction.DESC, "publishedAt")
                );
                return articleRepository.findByFilters(sec, cat, from, to, pageable);
            }

            // "All" tab — return mixed feed
            return getMixedFeed(sec, page, size);

        } catch (Exception e) {
            log.error("Error fetching articles: {}", e.getMessage());
            return Page.empty();
        }
    }

    // ══════════════════════════════════════════════════════
    //  MIXED FEED — balanced from all sources
    // ══════════════════════════════════════════════════════
    private Page<Article> getMixedFeed(Section section, int page, int size) {
        try {
            // Get ALL recent articles sorted by date
            Pageable all = PageRequest.of(0, 1000,
                    Sort.by(Sort.Direction.DESC, "publishedAt"));
            List<Article> allArticles = articleRepository
                    .findByFilters(section, null, null, null, all)
                    .getContent();

            // Group by source
            Map<String, List<Article>> bySource = allArticles.stream()
                    .collect(Collectors.groupingBy(
                            a -> a.getSourceName() != null ? a.getSourceName() : "Unknown"
                    ));

            log.info("Sources found: {}", bySource.keySet());

            // Limit Kaler Kantho to max 20% of feed
            int maxPerSource = Math.max(10, allArticles.size() / bySource.size());
            bySource.forEach((source, articles) -> {
                // Kaler Kantho has too many — strictly limit it
                if (source.toLowerCase().contains("kaler") ||
                        source.toLowerCase().contains("kantho")) {
                    int limit = Math.min(articles.size(), maxPerSource / 2);
                    bySource.put(source, articles.subList(0, limit));
                }
            });

            // Interleave — round robin from each source
            List<Article> mixed = interleaveBySource(bySource);

            // Sort mixed list: articles WITH images first in each batch
            List<Article> prioritized = prioritizeImages(mixed);

            // Total count
            int total = prioritized.size();
            int start = page * size;
            int end   = Math.min(start + size, total);

            if (start >= total) return Page.empty();

            List<Article> pageContent = prioritized.subList(start, end);
            return new PageImpl<>(pageContent,
                    PageRequest.of(page, size), total);

        } catch (Exception e) {
            log.error("Mixed feed error: {}", e.getMessage());
            Pageable pageable = PageRequest.of(page, size,
                    Sort.by(Sort.Direction.DESC, "publishedAt"));
            return articleRepository.findByFilters(section, null, null, null, pageable);
        }
    }

    /**
     * Round-robin interleave from all sources
     * BBC, AlJazeera, KalerKantho, ProthomAlo, BBC, AlJazeera...
     */
    private List<Article> interleaveBySource(Map<String, List<Article>> bySource) {
        List<List<Article>> lists = new ArrayList<>(bySource.values());
        // Sort each source by date
        lists.forEach(list -> list.sort(
                Comparator.comparing(Article::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
        ));

        List<Article> result = new ArrayList<>();
        int maxSize = lists.stream().mapToInt(List::size).max().orElse(0);

        for (int i = 0; i < maxSize; i++) {
            for (List<Article> sourceList : lists) {
                if (i < sourceList.size()) {
                    result.add(sourceList.get(i));
                }
            }
        }
        return result;
    }

    /**
     * In each group of 10, put articles with images first
     */
    private List<Article> prioritizeImages(List<Article> articles) {
        int groupSize = 10;
        List<Article> result = new ArrayList<>();

        for (int i = 0; i < articles.size(); i += groupSize) {
            int end = Math.min(i + groupSize, articles.size());
            List<Article> group = new ArrayList<>(articles.subList(i, end));

            // Sort group: with image first, without image last
            group.sort((a, b) -> {
                boolean aHasImg = hasImage(a);
                boolean bHasImg = hasImage(b);
                if (aHasImg && !bHasImg) return -1;
                if (!aHasImg && bHasImg) return 1;
                return 0;
            });
            result.addAll(group);
        }
        return result;
    }

    private boolean hasImage(Article a) {
        return a.getUrlToImage() != null &&
                !a.getUrlToImage().isBlank() &&
                a.getUrlToImage().startsWith("http");
    }

    // ══════════════════════════════════════════════════════
    //  OTHER METHODS
    // ══════════════════════════════════════════════════════
    public List<Article> getTrending(String section) {
        try {
            Section sec = section != null ?
                    Section.valueOf(section.toUpperCase()) : Section.BANGLADESH;
            return articleRepository
                    .findByIsTrendingTrueAndIsDeletedFalseAndSectionOrderByViewCountDesc(
                            sec, PageRequest.of(0, 10));
        } catch (Exception e) {
            log.error("Trending error: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Article> getBreaking() {
        try {
            return articleRepository
                    .findByIsBreakingTrueAndIsDeletedFalseOrderByPublishedAtDesc();
        } catch (Exception e) {
            log.error("Breaking error: {}", e.getMessage());
            return List.of();
        }
    }

    public Article getById(Long id) {
        return articleRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("Article not found: " + id));
    }

    @Async
    @Transactional
    public void incrementView(Long id) {
        try { articleRepository.incrementViewCount(id); }
        catch (Exception e) { log.warn("View increment failed: {}", e.getMessage()); }
    }

    public List<Article> search(String query, String section) {
        try { return articleRepository.searchByTitle(query, section); }
        catch (Exception e) { return List.of(); }
    }

    @Transactional
    @CacheEvict(value = {"articles","trending","breaking"}, allEntries = true)
    public Article markTrending(Long id, boolean trending) {
        Article a = getById(id);
        a.setIsTrending(trending);
        return articleRepository.save(a);
    }

    @Transactional
    @CacheEvict(value = {"articles","trending","breaking"}, allEntries = true)
    public Article markBreaking(Long id, boolean breaking) {
        Article a = getById(id);
        a.setIsBreaking(breaking);
        return articleRepository.save(a);
    }

    @Transactional
    @CacheEvict(value = {"articles","trending","breaking"}, allEntries = true)
    public void softDelete(Long id) {
        Article a = getById(id);
        a.setIsDeleted(true);
        articleRepository.save(a);
    }

    public Page<Article> getAllForAdmin(int page, int size) {
        return articleRepository.findByIsDeletedFalseOrderByPublishedAtDesc(
                PageRequest.of(page, size));
    }

    public long countAll() { return articleRepository.countByIsDeletedFalse(); }
    public long countTrending() {
        return articleRepository.countByIsTrendingTrueAndIsDeletedFalse();
    }

    private Section parseSection(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Section.valueOf(s.toUpperCase()); }
        catch (Exception e) { return null; }
    }

    private Category parseCategory(String c) {
        if (c == null || c.isBlank()) return null;
        try { return Category.valueOf(c.toUpperCase()); }
        catch (Exception e) { return null; }
    }
}