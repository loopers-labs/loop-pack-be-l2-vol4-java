package com.loopers.infrastructure.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_rank_monthly_product", columnNames = {"period_start", "product_id"}),
        @UniqueConstraint(name = "uk_product_rank_monthly_position", columnNames = {"period_start", "rank_position"})
    }
)
public class ProductRankMonthlyJpaEntity extends BaseEntity {
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "rank_position", nullable = false)
    private int rankPosition;
    @Column(name = "score", nullable = false, precision = 19, scale = 4)
    private BigDecimal score;
    @Column(name = "view_count", nullable = false)
    private long viewCount;
    @Column(name = "like_count", nullable = false)
    private long likeCount;
    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    protected ProductRankMonthlyJpaEntity() {
    }
}
