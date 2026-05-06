package com.newsbd.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class AppConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("articles", "trending", "breaking");
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .recordStats()
        );
        return manager;
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)); // 2MB
    }
}
