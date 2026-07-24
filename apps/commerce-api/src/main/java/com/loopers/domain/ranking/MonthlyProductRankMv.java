package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@IdClass(ProductRankMvId.class)
@Table(name = "mv_product_rank_monthly",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_mv_product_rank_monthly_rank", columnNames = {"period_start_date", "rank_no"})
    },
    indexes = {
        @Index(name = "idx_mv_product_rank_monthly_period_rank", columnList = "period_start_date, rank_no")
    })
public class MonthlyProductRankMv {

    @Id
    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sale_count", nullable = false)
    private long saleCount;

    @Column(name = "order_score", nullable = false)
    private double orderScore;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected MonthlyProductRankMv() {}
}
