package com.loopers.ranking.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/** 월간 랭킹 MV. period_key 는 KST 월 표기(`2026-07`). */
@Entity
@Table(
        name = "mv_product_rank_monthly",
        indexes = @Index(name = "idx_period_score", columnList = "period_key, score desc, product_id asc")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRankMonthly extends ProductRankMv {

    @EmbeddedId
    private ProductRankId id;

    public ProductRankMonthly(String periodKey, Long productId, double score,
                              long viewCount, long likeCount, long salesCount, ZonedDateTime createdAt) {
        super(score, viewCount, likeCount, salesCount, createdAt);
        this.id = new ProductRankId(periodKey, productId);
    }
}
