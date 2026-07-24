package com.loopers.infrastructure.ranking.mv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * mv_product_rank_monthly 매핑. PK/유니크 제약 구조는 MvProductRankWeeklyEntity와 동일하다(§2.5).
 */
@Getter
@Entity(name = "MvProductRankMonthly")
@Table(
    name = "mv_product_rank_monthly",
    indexes = @Index(name = "uk_mv_monthly_rank", columnList = "period_key, rank_position", unique = true)
)
@IdClass(MvProductRankMonthlyEntity.MvProductRankMonthlyId.class)
public class MvProductRankMonthlyEntity {

    @Id
    @Column(name = "period_key")
    private String periodKey;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected MvProductRankMonthlyEntity() {}

    public MvProductRankMonthlyEntity(
        String periodKey, Long productId, int rankPosition, double score, ZonedDateTime updatedAt
    ) {
        this.periodKey = periodKey;
        this.productId = productId;
        this.rankPosition = rankPosition;
        this.score = score;
        this.updatedAt = updatedAt;
    }

    public static class MvProductRankMonthlyId implements Serializable {
        private String periodKey;
        private Long productId;

        public MvProductRankMonthlyId() {}
    }
}
