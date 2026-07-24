package com.loopers.domain.metrics;

import com.loopers.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(
    name = "product_metrics",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_metrics_metric_date_product_id", columnNames = {"metric_date", "product_id"})
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "order_amount", nullable = false)
    private BigDecimal orderAmount = BigDecimal.ZERO;

    @Column(name = "daily_ranking_score", nullable = false)
    private double dailyRankingScore;

    private ProductMetrics(LocalDate metricDate, Long productId) {
        this.metricDate = metricDate;
        this.productId = productId;
    }

    public static ProductMetrics create(LocalDate metricDate, Long productId) {
        return new ProductMetrics(metricDate, productId);
    }

    public void addView(double scoreDelta) {
        this.viewCount++;
        this.dailyRankingScore += scoreDelta;
    }

    public void addLike(double scoreDelta) {
        this.likeCount++;
        this.dailyRankingScore += scoreDelta;
    }

    public void addSales(int amount, BigDecimal price, double scoreDelta) {
        this.salesCount += amount;
        this.orderAmount = this.orderAmount.add(price.multiply(BigDecimal.valueOf(amount)));
        this.dailyRankingScore += scoreDelta;
    }
}
