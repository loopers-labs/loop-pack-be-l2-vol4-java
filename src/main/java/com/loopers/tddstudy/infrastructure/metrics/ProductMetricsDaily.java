package com.loopers.tddstudy.infrastructure.metrics;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;


    @Entity
    @Table(
            name = "product_metrics_daily",
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_product_metric_date",
                    columnNames = {"product_id", "metric_date"}
            )
    )

    public class ProductMetricsDaily {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "product_id")
        private Long productId;

        @Column(name = "metric_date")
        private LocalDate metricDate;

        private long likeCount;
        private long salesCount;
        private long viewCount;
        private LocalDateTime createdAt;

        protected ProductMetricsDaily() {}

        public ProductMetricsDaily(Long productId, LocalDate metricDate,
                                   long likeCount, long salesCount, long viewCount) {
            this.productId = productId;
            this.metricDate = metricDate;
            this.likeCount = likeCount;
            this.salesCount = salesCount;
            this.viewCount = viewCount;
            this.createdAt = LocalDateTime.now();
        }

        public Long getId() { return id; }
        public Long getProductId() { return productId; }
        public LocalDate getMetricDate() { return metricDate; }
        public long getLikeCount() { return likeCount; }
        public long getSalesCount() { return salesCount; }
        public long getViewCount() { return viewCount; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }


