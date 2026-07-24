package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * 조회 전용 랭킹 MV의 공통 스키마. 주간/월간 테이블이 동일 구조라 공통 필드를 여기서 정의하고
 * 서브클래스가 각자의 테이블(mv_product_rank_weekly/monthly)에 매핑한다.
 * <p>
 * 기간은 (period_start, period_end)로 명시 저장한다 — 기간 정의가 운영 중 바뀌어도 각 행이
 * 자신이 커버한 구간을 스스로 설명하도록. rank는 배치가 미리 계산해 박아두어, 조회 시 정렬 없이
 * {@code WHERE period_start = ? ORDER BY ranking}로 바로 TOP N을 뽑는다.
 */
@MappedSuperclass
@IdClass(ProductRankId.class)
public abstract class ProductRankModel {

    @Id
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 1-based 순위. 예약어 회피를 위해 컬럼명은 ranking. */
    @Column(name = "ranking", nullable = false)
    private int ranking;

    /** 랭킹 점수(가중합). 배치 Processor가 계산한 값. */
    @Column(name = "score", nullable = false)
    private double score;

    /** 점수 산출 근거가 된 원지표(투명성·디버깅용). */
    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    /** 이 행이 집계된 시각(MV 신선도 확인용). */
    @Column(name = "aggregated_at", nullable = false)
    private ZonedDateTime aggregatedAt;

    protected ProductRankModel() {}

    protected ProductRankModel(LocalDate periodStart, LocalDate periodEnd, Long productId,
                               int ranking, double score, long likeCount, long salesCount,
                               ZonedDateTime aggregatedAt) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.productId = productId;
        this.ranking = ranking;
        this.score = score;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.aggregatedAt = aggregatedAt;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public Long getProductId() {
        return productId;
    }

    public int getRanking() {
        return ranking;
    }

    public double getScore() {
        return score;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getSalesCount() {
        return salesCount;
    }

    public ZonedDateTime getAggregatedAt() {
        return aggregatedAt;
    }
}
