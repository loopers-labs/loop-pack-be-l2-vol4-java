package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "mv_active_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvActiveVersionEntity {

    @Id
    @Column(name = "period_key", length = 20)
    private String periodKey;  // 예: "WEEKLY:202530", "MONTHLY:202507"

    @Column(name = "active_batch_id", nullable = false)
    private long activeBatchId;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public MvActiveVersionEntity(String periodKey, long activeBatchId) {
        this.periodKey = periodKey;
        this.activeBatchId = activeBatchId;
        this.updatedAt = ZonedDateTime.now();
    }

    public void activate(long batchId) {
        this.activeBatchId = batchId;
        this.updatedAt = ZonedDateTime.now();
    }
}
