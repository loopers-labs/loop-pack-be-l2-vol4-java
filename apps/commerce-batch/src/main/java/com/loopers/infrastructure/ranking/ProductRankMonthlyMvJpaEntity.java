package com.loopers.infrastructure.ranking;

import com.loopers.domain.BaseTimeEntity;
import com.loopers.domain.ranking.ProductRankSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "mv_product_rank_monthly")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRankMonthlyMvJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rank_start_date", nullable = false)
    private LocalDate rankStartDate;

    @Column(name = "rank_end_date", nullable = false)
    private LocalDate rankEndDate;

    @Column(name = "batch_run_id", nullable = false)
    private Long batchRunId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private boolean active;

    private ProductRankMonthlyMvJpaEntity(
        LocalDate rankStartDate,
        LocalDate rankEndDate,
        Long batchRunId,
        Long productId,
        int rankNo,
        double score,
        boolean active
    ) {
        this.rankStartDate = rankStartDate;
        this.rankEndDate = rankEndDate;
        this.batchRunId = batchRunId;
        this.productId = productId;
        this.rankNo = rankNo;
        this.score = score;
        this.active = active;
    }

    public static ProductRankMonthlyMvJpaEntity from(ProductRankSnapshot snapshot) {
        return new ProductRankMonthlyMvJpaEntity(
            snapshot.getRankStartDate(),
            snapshot.getRankEndDate(),
            snapshot.getBatchRunId(),
            snapshot.getProductId(),
            snapshot.getRankNo(),
            snapshot.getScore(),
            snapshot.isActive()
        );
    }
}
