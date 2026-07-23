package com.loopers.ranking.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 주간 TOP 100 랭킹 MV (mv_product_rank_weekly) 읽기 모델. */
@Entity
@Table(name = "mv_product_rank_weekly")
public class WeeklyProductRankModel extends ProductRankModel {

    protected WeeklyProductRankModel() {}

    public WeeklyProductRankModel(Long productId, int rank, double score) {
        super(productId, rank, score);
    }
}
