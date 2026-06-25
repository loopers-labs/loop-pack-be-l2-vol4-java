package com.loopers.product.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_like_summary_checkpoint")
public class ProductLikeSummaryCheckpoint {

    @Id
    @Column(name = "checkpoint_name", length = 50)
    private String checkpointName;

    @Column(name = "last_change_id", nullable = false)
    private long lastChangeId;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
