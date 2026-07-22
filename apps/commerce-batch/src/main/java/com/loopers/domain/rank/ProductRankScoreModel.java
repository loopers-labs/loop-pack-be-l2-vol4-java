package com.loopers.domain.rank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배치 채점 staging. Chunk Step이 기간 가중 점수를 (period_key, product_id) 단위로 upsert 하고, Tasklet이 TOP 100 을 잘라 MV 로 옮긴다.
 * 복합 자연키라 같은 기간 재실행 시 merge 로 덮어써져 멱등하다.
 */
@Entity
@Table(name = "product_rank_score")
@IdClass(ProductRankScoreId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRankScoreModel {

    @Id
    @Column(name = "period_key")
    private String periodKey;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "score", nullable = false)
    private double score;

    private ProductRankScoreModel(String periodKey, Long productId, double score) {
        this.periodKey = periodKey;
        this.productId = productId;
        this.score = score;
    }

    public static ProductRankScoreModel of(String periodKey, Long productId, double score) {
        return new ProductRankScoreModel(periodKey, productId, score);
    }
}