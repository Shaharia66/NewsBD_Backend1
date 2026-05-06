package com.newsbd.scheduler;

import com.newsbd.model.Article;
import com.newsbd.model.Article.Category;
import com.newsbd.model.Article.Section;
import com.newsbd.repository.ArticleRepository;
import com.newsbd.repository.NewsSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsFetcherScheduler {

    private final ArticleRepository    articleRepository;
    private final NewsSourceRepository newsSourceRepository;
    private final WebClient.Builder    webClientBuilder;

    @Value("${app.guardian.key}")
    private String guardianKey;

    @Value("${app.guardian.url}")
    private String guardianUrl;

    // ── Bangladesh RSS feeds (tested working) ──────────────
    private static final Map<String, String> BD_RSS_FEEDS = new LinkedHashMap<>() {{
        put("The Daily Star",  "https://thedailystar.net/rss.xml");
        put("Prothom Alo",     "https://prothomalo.com/feed");
        put("Kaler Kantho",    "https://www.kalerkantho.com/rss.xml");
        put("Samakal",         "https://samakal.com/feed");
        put("Jugantor",        "https://www.jugantor.com/rss.xml");
    }};

    // ── International RSS feeds (tested working) ───────────
    private static final Map<String, String> INTL_RSS_FEEDS = new LinkedHashMap<>() {{
        put("BBC News",        "https://feeds.bbci.co.uk/news/rss.xml");
        put("BBC Technology",  "https://feeds.bbci.co.uk/news/technology/rss.xml");
        put("BBC Sport",       "https://feeds.bbci.co.uk/sport/rss.xml");
        put("Al Jazeera",      "https://www.aljazeera.com/xml/rss/all.xml");
        put("Sky News",        "https://feeds.skynews.com/feeds/rss/world.xml");
        put("NPR News",        "https://feeds.npr.org/1001/rss.xml");
        put("NPR Technology",  "https://feeds.npr.org/1019/rss.xml");
        put("NPR Business",    "https://feeds.npr.org/1006/rss.xml");
    }};

    // ── Guardian sections ──────────────────────────────────
    private static final String[] GUARDIAN_SECTIONS = {
            "world", "technology", "sport", "politics", "business", "environment"
    };

    // ══════════════════════════════════════════════════════
    //  MAIN SCHEDULER
    // ══════════════════════════════════════════════════════
    @Scheduled(cron = "${app.scheduler.news-fetch-cron}")
    @CacheEvict(value = {"articles", "trending", "breaking"}, allEntries = true)
    public void fetchAllNews() {
        log.info("========== News fetch cycle started ==========");
        int total = 0;

        // 1. Bangladesh RSS
        total += fetchRssFeeds(BD_RSS_FEEDS, Section.BANGLADESH);

        // 2. International RSS
        total += fetchRssFeeds(INTL_RSS_FEEDS, Section.INTERNATIONAL);

        // 3. Guardian API
        total += fetchGuardianNews();

        log.info("========== News fetch complete. {} new articles saved. ==========", total);
    }

    // ══════════════════════════════════════════════════════
    //  RSS FEED FETCHER
    // ══════════════════════════════════════════════════════
    private int fetchRssFeeds(Map<String, String> feeds, Section section) {
        int count = 0;
        for (Map.Entry<String, String> entry : feeds.entrySet()) {
            String sourceName = entry.getKey();
            String feedUrl    = entry.getValue();
            try {
                log.info("Fetching RSS: {} — {}", sourceName, feedUrl);
                count += parseRssFeed(feedUrl, sourceName, section);
                Thread.sleep(800);
            } catch (Exception e) {
                log.error("RSS fetch error for {}: {}", sourceName, e.getMessage());
            }
        }
        return count;
    }

    private int parseRssFeed(String feedUrl, String sourceName, Section section) {
        int saved = 0;
        try {
            // Use HttpURLConnection with browser User-Agent to avoid 403
            URL url = new URL(feedUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.connect();

            if (conn.getResponseCode() != 200) {
                log.error("RSS {} returned HTTP {}", sourceName, conn.getResponseCode());
                return 0;
            }

            InputStream stream = conn.getInputStream();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null); // suppress XML warnings
            Document doc = builder.parse(stream);
            doc.getDocumentElement().normalize();

            NodeList items = doc.getElementsByTagName("item");
            log.info("RSS {}: found {} items", sourceName, items.getLength());

            for (int i = 0; i < items.getLength(); i++) {
                try {
                    Element item = (Element) items.item(i);
                    String title       = getTagValue(item, "title");
                    String link        = getTagValue(item, "link");
                    String description = getTagValue(item, "description");
                    String pubDate     = getTagValue(item, "pubDate");
                    String thumbnail   = getMediaThumbnail(item);

                    if (link == null || link.isBlank()) continue;
                    if (title == null || title.isBlank()) continue;

                    String hash = md5(link.trim());
                    if (articleRepository.existsByUrlHash(hash)) continue;

                    // Clean HTML from description
                    if (description != null) {
                        description = description.replaceAll("<[^>]*>", "").trim();
                        if (description.length() > 500) description = description.substring(0, 500) + "...";
                    }

                    Article article = Article.builder()
                            .urlHash(hash)
                            .title(title.trim())
                            .description(description)
                            .url(link.trim())
                            .urlToImage(thumbnail)
                            .sourceName(sourceName)
                            .section(section)
                            .category(mapCategory(title, section))
                            .publishedAt(parseRssDate(pubDate))
                            .build();

                    articleRepository.save(article);
                    saved++;
                } catch (Exception e) {
                    log.warn("Failed to save RSS item from {}: {}", sourceName, e.getMessage());
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            log.error("Failed to parse RSS feed {}: {}", feedUrl, e.getMessage());
        }
        return saved;
    }

    private String getTagValue(Element item, String tagName) {
        NodeList nodes = item.getElementsByTagName(tagName);
        if (nodes.getLength() > 0 && nodes.item(0) != null) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private String getMediaThumbnail(Element item) {
        // Try media:thumbnail
        NodeList media = item.getElementsByTagName("media:thumbnail");
        if (media.getLength() > 0) {
            Element el = (Element) media.item(0);
            String u = el.getAttribute("url");
            if (u != null && !u.isBlank()) return u;
        }
        // Try media:content
        NodeList mediaContent = item.getElementsByTagName("media:content");
        if (mediaContent.getLength() > 0) {
            Element el = (Element) mediaContent.item(0);
            String u = el.getAttribute("url");
            if (u != null && !u.isBlank() && el.getAttribute("medium").equals("image")) return u;
        }
        // Try enclosure
        NodeList enclosure = item.getElementsByTagName("enclosure");
        if (enclosure.getLength() > 0) {
            Element el = (Element) enclosure.item(0);
            String type = el.getAttribute("type");
            if (type != null && type.startsWith("image")) {
                return el.getAttribute("url");
            }
        }
        return null;
    }

    // ══════════════════════════════════════════════════════
    //  GUARDIAN API
    // ══════════════════════════════════════════════════════
    private int fetchGuardianNews() {
        int count = 0;
        WebClient client = webClientBuilder.baseUrl(guardianUrl).build();

        for (String section : GUARDIAN_SECTIONS) {
            try {
                Map<?, ?> response = client.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/search")
                                .queryParam("section", section)
                                .queryParam("show-fields", "trailText,thumbnail")
                                .queryParam("page-size", "10")
                                .queryParam("api-key", guardianKey)
                                .build())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (response != null) {
                    Map<?, ?> resp = (Map<?, ?>) response.get("response");
                    if (resp != null && resp.get("results") instanceof List<?> results) {
                        count += saveGuardianArticles(results);
                    }
                }
                Thread.sleep(600);
            } catch (Exception e) {
                log.error("Guardian fetch error for '{}': {}", section, e.getMessage());
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int saveGuardianArticles(List<?> results) {
        int saved = 0;
        for (Object item : results) {
            try {
                Map<String, Object> art = (Map<String, Object>) item;
                String url = (String) art.get("webUrl");
                if (url == null) continue;

                String hash = md5(url);
                if (articleRepository.existsByUrlHash(hash)) continue;

                Map<String, Object> fields = (Map<String, Object>) art.get("fields");
                String title       = (String) art.get("webTitle");
                String description = fields != null ? (String) fields.get("trailText") : null;
                String thumbnail   = fields != null ? (String) fields.get("thumbnail")  : null;

                Article article = Article.builder()
                        .urlHash(hash)
                        .title(title)
                        .description(description)
                        .url(url)
                        .urlToImage(thumbnail)
                        .sourceName("The Guardian")
                        .section(Section.INTERNATIONAL)
                        .category(mapGuardianSection((String) art.get("sectionId")))
                        .publishedAt(parseDate((String) art.get("webPublicationDate")))
                        .build();

                articleRepository.save(article);
                saved++;
            } catch (Exception e) {
                log.warn("Failed to save Guardian article: {}", e.getMessage());
            }
        }
        return saved;
    }

    // ══════════════════════════════════════════════════════
    //  CATEGORY MAPPERS
    // ══════════════════════════════════════════════════════
    private Category mapCategory(String title, Section section) {
        if (title == null) return section == Section.BANGLADESH ? Category.LOCAL : Category.INTERNATIONAL;
        String t = title.toLowerCase();
        if (t.contains("tech") || t.contains("ai") || t.contains("software") || t.contains("digital") || t.contains("cyber") || t.contains("startup") || t.contains("প্রযুক্তি") || t.contains("ডিজিটাল")) return Category.TECH;
        if (t.contains("cricket") || t.contains("football") || t.contains("sport") || t.contains("match") || t.contains("bcb") || t.contains("fifa") || t.contains("icc") || t.contains("খেলা") || t.contains("ক্রিকেট")) return Category.SPORTS;
        if (t.contains("election") || t.contains("parliament") || t.contains("minister") || t.contains("government") || t.contains("political") || t.contains("vote") || t.contains("রাজনীতি") || t.contains("নির্বাচন") || t.contains("সরকার")) return Category.POLITICS;
        if (t.contains("economy") || t.contains("trade") || t.contains("export") || t.contains("business") || t.contains("bank") || t.contains("market") || t.contains("অর্থনীতি") || t.contains("ব্যবসা") || t.contains("বাজার")) return Category.BUSINESS;
        if (t.contains("health") || t.contains("hospital") || t.contains("disease") || t.contains("medical") || t.contains("vaccine") || t.contains("স্বাস্থ্য") || t.contains("হাসপাতাল")) return Category.HEALTH;
        if (t.contains("film") || t.contains("music") || t.contains("entertainment") || t.contains("বিনোদন") || t.contains("সিনেমা")) return Category.ENTERTAINMENT;
        return section == Section.BANGLADESH ? Category.LOCAL : Category.INTERNATIONAL;
    }

    private Category mapGuardianSection(String sectionId) {
        if (sectionId == null) return Category.INTERNATIONAL;
        return switch (sectionId) {
            case "technology"                         -> Category.TECH;
            case "sport"                              -> Category.SPORTS;
            case "politics"                           -> Category.POLITICS;
            case "business"                           -> Category.BUSINESS;
            case "lifeandstyle", "healthcare-network" -> Category.HEALTH;
            case "culture", "film", "music"           -> Category.ENTERTAINMENT;
            default                                   -> Category.INTERNATIONAL;
        };
    }

    // ══════════════════════════════════════════════════════
    //  DATE PARSERS
    // ══════════════════════════════════════════════════════
    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null) return LocalDateTime.now();
        try {
            String clean = dateStr.replace("Z", "").replaceAll("\\+\\d{2}:\\d{2}$", "");
            return LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } catch (Exception e) { return LocalDateTime.now(); }
    }

    private LocalDateTime parseRssDate(String dateStr) {
        if (dateStr == null) return LocalDateTime.now();
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
            return LocalDateTime.parse(dateStr.trim(), f);
        } catch (Exception e1) {
            try {
                String clean = dateStr.replace("Z", "").replaceAll("\\+\\d{2}:\\d{2}$", "");
                return LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            } catch (Exception e2) { return LocalDateTime.now(); }
        }
    }

    // ══════════════════════════════════════════════════════
    //  MD5 HASH
    // ══════════════════════════════════════════════════════
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return String.valueOf(input.hashCode()); }
    }
}