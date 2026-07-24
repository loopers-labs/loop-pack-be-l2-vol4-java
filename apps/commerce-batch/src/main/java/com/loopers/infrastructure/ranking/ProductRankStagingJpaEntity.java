package com.loopers.infrastructure.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "product_rank_staging",
    indexes = {
        @Index(
            name = "idx_product_rank_staging_publish",
            columnList = "run_key, score DESC, product_id"
        ),
        @Index(
            name = "idx_product_rank_staging_cleanup",
            columnList = "created_at, period_type, period_start"
        )
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uk_product_rank_staging_run_product",
        columnNames = {"run_key", "product_id"}
    )
)
public class ProductRankStagingJpaEntity extends BaseEntity {
    @Column(name = "run_key", nullable = false, length = 80)
    private String runKey;
    @Column(name = "period_type", nullable = false, length = 10)
    private String periodType;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "job_instance_id", nullable = false)
    private Long jobInstanceId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "score", nullable = false, precision = 19, scale = 4)
    private BigDecimal score;
    @Column(name = "view_count", nullable = false)
    private long viewCount;
    @Column(name = "like_count", nullable = false)
    private long likeCount;
    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    protected ProductRankStagingJpaEntity() {
    }
}
