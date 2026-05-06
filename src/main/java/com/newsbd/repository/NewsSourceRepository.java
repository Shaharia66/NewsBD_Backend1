package com.newsbd.repository;

import com.newsbd.model.NewsSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {
    List<NewsSource> findByIsTrustedTrueAndIsBlockedFalse();
    List<NewsSource> findAllByOrderByCreatedAtDesc();
    long countByIsBlockedFalse();
}
