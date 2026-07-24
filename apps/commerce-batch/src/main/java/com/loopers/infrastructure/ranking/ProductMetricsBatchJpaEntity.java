package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;

@Getter
@Entity
@Immutable
@Table(name = "product_metrics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsBatchJpaEntity {

    @Id
    private Long id;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "daily_ranking_score", nullable = false)
    private double dailyRankingScore;

    ProductMetricsBatchJpaEntity(Long id, LocalDate metricDate, Long productId, double dailyRankingScore) {
        this.id = id;
        this.metricDate = metricDate;
        this.productId = productId;
        this.dailyRankingScore = dailyRankingScore;
    }
}
