package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.news.News;
import com.nurseli.marketdata.domain.news.NewsCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NewsRepository extends JpaRepository<News, Long> {

    Optional<News> findByExternalId(String externalId);

    Page<News> findByCategoryOrderByPublishedAtDesc(
            NewsCategory category,
            Pageable pageable
    );

    Page<News> findAllByOrderByPublishedAtDesc(Pageable pageable);

    @Query("""
        SELECT n FROM News n
        WHERE (:category IS NULL OR n.category = :category)
        AND n.publishedAt >= :since
        ORDER BY n.publishedAt DESC
    """)
    Page<News> findRecentNews(
            @Param("category") NewsCategory category,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );
}