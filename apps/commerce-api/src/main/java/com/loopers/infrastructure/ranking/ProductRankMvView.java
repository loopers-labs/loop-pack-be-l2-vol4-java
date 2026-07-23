package com.loopers.infrastructure.ranking;

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
 * commerce-batch 가 적재하는 주간/월간 랭킹 MV 의 조회 전용 매핑. 앱 간 직접 의존을 피하려 같은 스키마를 API 쪽에도 둔다.
 * 주간/월간은 테이블만 다르고 구조가 같아 MappedSuperclass 로 공유한다.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ProductRankMvView {

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

    protected ProductRankMvView(String periodKey, int rankNo, Long productId, double score, ZonedDateTime aggregatedAt) {
        this.periodKey = periodKey;
        this.rankNo = rankNo;
        this.productId = productId;
        this.score = score;
        this.aggregatedAt = aggregatedAt;
    }
}