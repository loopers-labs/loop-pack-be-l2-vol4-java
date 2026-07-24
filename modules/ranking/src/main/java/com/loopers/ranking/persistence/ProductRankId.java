package com.loopers.ranking.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class ProductRankId implements Serializable {

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    // Hibernate legacy DDL orders embeddable attributes by name; keep this after snapshotId for the snapshot-leading PK.
    @Column(name = "product_id", nullable = false)
    private Long targetProductId;

    public ProductRankId(Long snapshotId, Long productId) {
        this.snapshotId = snapshotId;
        this.targetProductId = productId;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public Long getProductId() {
        return targetProductId;
    }
}
