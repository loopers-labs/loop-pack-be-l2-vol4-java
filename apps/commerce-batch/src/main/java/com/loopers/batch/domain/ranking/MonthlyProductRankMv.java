package com.loopers.batch.domain.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 월간 랭킹 MV(기준일로부터 지난 30일 집계의 상위 N위). 배치가 window(snapshot_date) 단위로 교체 적재한다.
 * uk(snapshot_date, product_id): 한 스냅샷에 상품은 한 번만. idx(snapshot_date, rank_no): 순위순 조회를 위한 인덱스.
 */
@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = @UniqueConstraint(name = "uk_mv_monthly_snapshot_product", columnNames = {"snapshot_date", "product_id"}),
    indexes = @Index(name = "idx_mv_monthly_snapshot_rank", columnList = "snapshot_date, rank_no")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyProductRankMv extends ProductRankMv {

    public MonthlyProductRankMv(LocalDate snapshotDate, int rank, Long productId, double score) {
        super(snapshotDate, rank, productId, score);
    }
}
