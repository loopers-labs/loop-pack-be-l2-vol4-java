package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "mv_product_rank_weekly",
    uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "year_week", "batch_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankWeeklyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID productId;

    @Column(name = "ranking_order", nullable = false)
    private int rankingOrder;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "year_week", nullable = false)
    private int yearWeek;

    @Column(name = "batch_id", nullable = false)
    private long batchId;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public MvProductRankWeeklyEntity(UUID productId, int rank, double score, int yearWeek, long batchId) {
        this.productId = productId;
        this.rankingOrder = rank;
        this.score = score;
        this.yearWeek = yearWeek;
        this.batchId = batchId;
        this.updatedAt = ZonedDateTime.now();
    }
}
