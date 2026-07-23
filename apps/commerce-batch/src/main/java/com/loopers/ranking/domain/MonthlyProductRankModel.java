package com.loopers.ranking.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 월간 TOP 100 랭킹 MV (mv_product_rank_monthly).
 */
@Entity
@Table(name = "mv_product_rank_monthly")
public class MonthlyProductRankModel extends ProductRankModel {

    protected MonthlyProductRankModel() {}

    public MonthlyProductRankModel(Long productId, int rank, double score) {
        super(productId, rank, score);
    }
}
