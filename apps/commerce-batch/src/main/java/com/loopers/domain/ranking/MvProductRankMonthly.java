package com.loopers.domain.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 월간 TOP 100 랭킹 MV. (기간, 상품) 유니크로 재실행 시 중복 적재를 DB가 막고, (기간, 순위)는 조회 정렬용. */
@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = @UniqueConstraint(name = "uq_monthly_period_product", columnNames = {"period_key", "product_id"}),
    indexes = @Index(name = "idx_monthly_period_rank", columnList = "period_key, rank_no"))
public class MvProductRankMonthly extends MvProductRank {

    protected MvProductRankMonthly() {
    }

    public MvProductRankMonthly(String periodKey, int rankNo, RankAggregate aggregate) {
        super(periodKey, rankNo, aggregate);
    }
}
