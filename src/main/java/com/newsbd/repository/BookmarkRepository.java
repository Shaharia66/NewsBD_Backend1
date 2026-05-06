package com.newsbd.repository;

import com.newsbd.model.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    @Query("SELECT b FROM Bookmark b JOIN FETCH b.article WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    List<Bookmark> findByUserIdWithArticles(@Param("userId") Long userId);

    Optional<Bookmark> findByUserIdAndArticleId(Long userId, Long articleId);

    boolean existsByUserIdAndArticleId(Long userId, Long articleId);

    void deleteByUserIdAndArticleId(Long userId, Long articleId);
}
