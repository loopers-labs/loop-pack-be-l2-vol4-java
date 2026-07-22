package com.loopers.ranking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 확정된 과거 랭킹 스냅샷. 하루가 지나면(00:30 finalize) 그날 ZSET 을 순위와 함께 박아두는 불변 기록이다.
 * TTL 로 사라지는 ZSET 과 달리 영속되어, 서빙 창(2일) 밖 과거 조회·재구축의 원천이 된다.
 */
@Entity
@Table(name = "ranking_snapshot", indexes = @Index(name = "idx_ranking_snapshot_date", columnList = "stat_date, rank_position"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    @Column(nullable = false)
    private double score;

    public RankingSnapshot(LocalDate statDate, Long productId, int rankPosition, double score) {
        this.statDate = statDate;
        this.productId = productId;
        this.rankPosition = rankPosition;
        this.score = score;
    }
}
