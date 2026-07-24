package com.loopers.infrastructure.ranking.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.io.Serializable;

/**
 * mv_product_rank_staging 매핑. aggregateStep이 Top100 산출 결과를 임시로 담아두고,
 * publishStep의 검증(RankingStagingSnapshotValidator) 대상이 된다.
 */
@Getter
@Entity(name = "MvProductRankStaging")
@Table(
    name = "mv_product_rank_staging",
    indexes = @Index(name = "uk_staging_rank", columnList = "period_type, period_key, rank_position", unique = true)
)
@IdClass(MvProductRankStagingEntity.StagingId.class)
public class MvProductRankStagingEntity {

    @Id
    @Column(name = "period_type")
    private String periodType;

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

    protected MvProductRankStagingEntity() {}

    public MvProductRankStagingEntity(String periodType, String periodKey, Long productId, int rankPosition, double score) {
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.productId = productId;
        this.rankPosition = rankPosition;
        this.score = score;
    }

    public static class StagingId implements Serializable {
        private String periodType;
        private String periodKey;
        private Long productId;

        public StagingId() {}
    }
}
