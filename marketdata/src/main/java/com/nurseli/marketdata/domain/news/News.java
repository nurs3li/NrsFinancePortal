package com.nurseli.marketdata.domain.news;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "news", indexes = {
        @Index(name = "idx_news_category_published", columnList = "category,published_at"),
        @Index(name = "idx_news_external_id", columnList = "external_id", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", length = 100)
    private String externalId; // FinHub'dan gelen ID (duplicate önleme)

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "title_tr", length = 500)
    private String titleTr;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "summary_tr", columnDefinition = "TEXT")
    private String summaryTr;

    @Column(length = 100)
    private String source; // FinHub, Bloomberg, vb.

    @Column(length = 1000)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsCategory category;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}