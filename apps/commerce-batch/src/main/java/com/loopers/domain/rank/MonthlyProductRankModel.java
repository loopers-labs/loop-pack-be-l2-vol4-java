package com.loopers.domain.rank;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * 월간 TOP 100 랭킹 MV (`mv_product_rank_monthly`). 배치가 매 실행 시 해당 기간 스냅샷만 교체(clear→insert)하고
 * 과거 기간은 보존한다(히스토리). 조회는 기간(period_start·period_end)으로 스냅샷을 고른 뒤 rank_no 순으로 읽는다.
 */
@Entity
@Table(
    name = "mv_product_rank_monthly",
    indexes = @Index(name = "idx_mv_monthly_period_rank", columnList = "period_start, period_end, rank_no")
)
public class MonthlyProductRankModel extends ProductRankSnapshotModel {

    protected MonthlyProductRankModel() {}

    public MonthlyProductRankModel(int rankNo, Long productId, double score, LocalDate periodStart, LocalDate periodEnd) {
        super(rankNo, productId, score, periodStart, periodEnd);
    }
}
