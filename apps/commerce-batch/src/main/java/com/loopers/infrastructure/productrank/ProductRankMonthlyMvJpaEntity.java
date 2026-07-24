package com.loopers.infrastructure.productrank;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

// 실제 쓰기는 ProductRankMvUpsertWriter가 JdbcTemplate 네이티브 upsert로 수행한다 — 이 엔티티는
// test 프로필 ddl-auto=create가 스키마를 생성하고 DatabaseCleanUp이 테이블을 인식하게 하기 위한 목적.
@Entity
@Table(name = "mv_product_rank_monthly")
@Getter
public class ProductRankMonthlyMvJpaEntity {

    @EmbeddedId
    private ProductRankMvId id;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "view_sum", nullable = false)
    private long viewSum;

    @Column(name = "like_delta_sum", nullable = false)
    private long likeDeltaSum;

    @Column(name = "purchase_quantity_sum", nullable = false)
    private long purchaseQuantitySum;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    protected ProductRankMonthlyMvJpaEntity() {}
}
