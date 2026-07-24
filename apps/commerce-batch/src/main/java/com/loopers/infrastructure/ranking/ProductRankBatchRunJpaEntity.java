package com.loopers.infrastructure.ranking;

import com.loopers.domain.BaseTimeEntity;
import com.loopers.domain.ranking.BatchRunStatus;
import com.loopers.domain.ranking.ProductRankBatchRun;
import com.loopers.domain.ranking.RankingPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "product_rank_batch_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRankBatchRunJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RankingPeriod period;

    @Column(name = "rank_start_date", nullable = false)
    private LocalDate rankStartDate;

    @Column(name = "rank_end_date", nullable = false)
    private LocalDate rankEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchRunStatus status;

    public ProductRankBatchRunJpaEntity(
        Long id,
        RankingPeriod period,
        LocalDate rankStartDate,
        LocalDate rankEndDate,
        BatchRunStatus status
    ) {
        this.id = id;
        this.period = period;
        this.rankStartDate = rankStartDate;
        this.rankEndDate = rankEndDate;
        this.status = status;
    }

    public static ProductRankBatchRunJpaEntity from(ProductRankBatchRun batchRun) {
        return new ProductRankBatchRunJpaEntity(
            batchRun.getId(),
            batchRun.getPeriod(),
            batchRun.getRankStartDate(),
            batchRun.getRankEndDate(),
            batchRun.getStatus()
        );
    }

    public ProductRankBatchRun toDomain() {
        return ProductRankBatchRun.restore(id, period, rankStartDate, rankEndDate, status);
    }
}
