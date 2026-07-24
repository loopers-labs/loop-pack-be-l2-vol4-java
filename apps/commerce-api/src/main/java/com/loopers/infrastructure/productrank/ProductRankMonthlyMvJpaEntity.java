package com.loopers.infrastructure.productrank;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

// 실제 조회는 ProductRankRepositoryImpl이 이 엔티티를 통해 JPA로 수행한다.
// 쓰기는 commerce-batch의 ProductRankMvUpsertWriter가 네이티브 upsert로 별도 수행하므로
// 여기서는 세터를 노출하지 않는다.
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
