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
 * mv_product_rank_weekly 매핑. PK는 (period_key, product_id) — 같은 기간에 같은 상품이
 * 중복 게시되지 않도록 보장하고, rank_position 유니크 인덱스로 중복 순위도 함께 방지한다(§2.5).
 */
@Getter
@Entity(name = "MvProductRankWeekly")
@Table(
    name = "mv_product_rank_weekly",
    indexes = @Index(name = "uk_mv_weekly_rank", columnList = "period_key, rank_position", unique = true)
)
@IdClass(MvProductRankWeeklyEntity.MvProductRankWeeklyId.class)
public class MvProductRankWeeklyEntity {

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

    protected MvProductRankWeeklyEntity() {}

    public MvProductRankWeeklyEntity(
        String periodKey, Long productId, int rankPosition, double score, ZonedDateTime updatedAt
    ) {
        this.periodKey = periodKey;
        this.productId = productId;
        this.rankPosition = rankPosition;
        this.score = score;
        this.updatedAt = updatedAt;
    }

    public static class MvProductRankWeeklyId implements Serializable {
        private String periodKey;
        private Long productId;

        public MvProductRankWeeklyId() {}
    }
}
