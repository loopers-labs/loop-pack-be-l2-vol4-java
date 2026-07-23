package com.loopers.domain.rank;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 주간/월간 랭킹 MV(조회 전용) 공통 매핑. period_key 안에서 rank_no(1..100) 로 사전 정렬된 TOP 100 을 담는다.
 * 주간/월간은 테이블만 다르고 구조가 같아 MappedSuperclass 로 공유한다. rank 는 MySQL 예약어라 컬럼명을 rank_no 로 둔다.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ProductRankMv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_key", nullable = false)
    private String periodKey;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "aggregated_at", nullable = false)
    private ZonedDateTime aggregatedAt;

    protected ProductRankMv(String periodKey, int rankNo, Long productId, double score, ZonedDateTime aggregatedAt) {
        this.periodKey = periodKey;
        this.rankNo = rankNo;
        this.productId = productId;
        this.score = score;
        this.aggregatedAt = aggregatedAt;
    }
}