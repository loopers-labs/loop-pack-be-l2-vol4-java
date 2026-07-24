package com.loopers.infrastructure.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "product_rank_job_lock",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_product_rank_job_lock_period",
        columnNames = {"period_type", "period_start"}
    )
)
public class ProductRankJobLockJpaEntity extends BaseEntity {
    @Column(name = "period_type", nullable = false, length = 10)
    private String periodType;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected ProductRankJobLockJpaEntity() {
    }
}
