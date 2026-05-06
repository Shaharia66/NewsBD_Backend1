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
    //  MAIN FEED — mixed from all sources
    // ══════════════════════════════════════════════════════
    public Page<Article> getArticles(String section, String category,
                                     LocalDateTime from, LocalDateTime to,
                                     int page, int size) {
        try {
            Section sec = parseSection(section);
            Category cat = parseCategory(category);
            Pageable pageable = PageRequest.of(
                    Math.max(page, 0),
                    Math.min(size, 50),
                    Sort.by(Sort.Direction.DESC, "publishedAt")
            );

            // If no filters — return mixed feed from all sources
            if (cat == null && from == null && to == null) {
                return getMixedFeed(sec, page, size);
            }

            return articleRepository.findByFilters(sec, cat, from, to, pageable);

        } catch (Exception e) {
            log.error("Error fetching articles: {}", e.getMessage());
            return Page.empty();
        }
    }

    // ── Mixed feed — max 3 articles per source, then sorted by date ──
    private Page<Article> getMixedFeed(Section section, int page, int size) {
        try {
            String sectionStr = section != null ? section.name() : null;

            // Get articles mixed from all sources (max 3 per source)
            List<Article> allMixed = articleRepository.findMixedFromAllSources(
                    sectionStr, 200  // get 200 mixed articles
            );

            // Shuffle to mix sources, then sort by date
            List<Article> shuffled = interleaveBySource(allMixed);

            // Manual pagination
            int start = page * size;
            int end   = Math.min(start + size, shuffled.size());

            if (start >= shuffled.size()) return Page.empty();

            List<Article> pageContent = shuffled.subList(start, end);
            return new PageImpl<>(pageContent, PageRequest.of(page, size), shuffled.size());

        } catch (Exception e) {
            log.error("Mixed feed error, falling back: {}", e.getMessage());
            // Fallback to normal query
            Section sec = section;
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
            return articleRepository.findByFilters(sec, null, null, null, pageable);
        }
    }

    /**
     * Interleave articles from different sources
     * So feed shows: BBC, AlJazeera, KalerKantho, ProthomAlo, BBC, AlJazeera...
     * Instead of: KalerKantho x100, then BBC x50...
     */
    private List<Article> interleaveBySource(List<Article> articles) {
        // Group by source
        Map<String, List<Article>> bySource = articles.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getSourceName() != null ? a.getSourceName() : "Unknown"
                ));

        List<Article> result = new ArrayList<>();
        List<List<Article>> sourceLists = new ArrayList<>(bySource.values());

        // Sort each source's articles by date
        sourceLists.forEach(list -> list.sort(
                Comparator.comparing(Article::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
        ));

        // Round-robin interleave
        int maxSize = sourceLists.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < maxSize; i++) {
            for (List<Article> sourceList : sourceLists) {
                if (i < sourceList.size()) {
                    result.add(sourceList.get(i));
                }
            }
        }

        return result;
    }

    // ══════════════════════════════════════════════════════
    //  OTHER METHODS
    // ══════════════════════════════════════════════════════
    @Cacheable(value = "trending", key = "#section")
    public List<Article> getTrending(String section) {
        try {
            Section sec = section != null ? Section.valueOf(section.toUpperCase()) : Section.BANGLADESH;
            return articleRepository
                    .findByIsTrendingTrueAndIsDeletedFalseAndSectionOrderByViewCountDesc(
                            sec, PageRequest.of(0, 10)
                    );
        } catch (Exception e) {
            log.error("Error fetching trending: {}", e.getMessage());
            return List.of();
        }
    }

    @Cacheable(value = "breaking")
    public List<Article> getBreaking() {
        try {
            return articleRepository.findByIsBreakingTrueAndIsDeletedFalseOrderByPublishedAtDesc();
        } catch (Exception e) {
            log.error("Error fetching breaking: {}", e.getMessage());
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
        catch (Exception e) { log.warn("Could not increment view for {}: {}", id, e.getMessage()); }
    }

    public List<Article> search(String query, String section) {
        try { return articleRepository.searchByTitle(query, section); }
        catch (Exception e) { log.error("Search error: {}", e.getMessage()); return List.of(); }
    }

    @Transactional
    @CacheEvict(value = {"articles", "trending", "breaking"}, allEntries = true)
    public Article markTrending(Long id, boolean trending) {
        Article a = getById(id); a.setIsTrending(trending); return articleRepository.save(a);
    }

    @Transactional
    @CacheEvict(value = {"articles", "trending", "breaking"}, allEntries = true)
    public Article markBreaking(Long id, boolean breaking) {
        Article a = getById(id); a.setIsBreaking(breaking); return articleRepository.save(a);
    }

    @Transactional
    @CacheEvict(value = {"articles", "trending", "breaking"}, allEntries = true)
    public void softDelete(Long id) {
        Article a = getById(id); a.setIsDeleted(true); articleRepository.save(a);
    }

    public Page<Article> getAllForAdmin(int page, int size) {
        return articleRepository.findByIsDeletedFalseOrderByPublishedAtDesc(PageRequest.of(page, size));
    }

    public long countAll()      { return articleRepository.countByIsDeletedFalse(); }
    public long countTrending() { return articleRepository.countByIsTrendingTrueAndIsDeletedFalse(); }

    private Section parseSection(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Section.valueOf(s.toUpperCase()); } catch (Exception e) { return null; }
    }

    private Category parseCategory(String c) {
        if (c == null || c.isBlank()) return null;
        try { return Category.valueOf(c.toUpperCase()); } catch (Exception e) { return null; }
    }
}