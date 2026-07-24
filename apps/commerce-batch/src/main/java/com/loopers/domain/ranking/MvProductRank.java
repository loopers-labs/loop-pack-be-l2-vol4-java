package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * 주간/월간 랭킹 Materialized View 의 공통 모델. 이 배치가 MV 의 생산자이자 스키마 소유자다
 * (commerce-api 는 읽기 전용 projection 으로만 접근한다 — 조회 앱이 ddl-auto 로 MV 를 날리지 않도록).
 *
 * <p>{@code rankNo} 는 적재 시점엔 null 이고, 전체 적재가 끝난 뒤 rank 단계에서 상위 N 개에만 부여된다.
 * 랭킹은 "전체 정렬 후 상위 N"이라 청크 스트리밍만으로는 확정할 수 없기 때문이다.
 */
@MappedSuperclass
public abstract class MvProductRank extends BaseEntity {

    @Column(name = "period_key", nullable = false, length = 16)
    private String periodKey;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 확정 순위(1-based). 상위 N 밖은 rank 단계에서 삭제되므로 최종 상태에서는 항상 non-null. */
    @Column(name = "rank_no")
    private Integer rankNo;

    @Column(name = "score", nullable = false)
    private double score;

    /** 기간 내 좋아요 순증감 합(취소 반영 — 음수 가능). */
    @Column(name = "like_count", nullable = false)
    private long likeCount;

    /** 기간 내 판매 수량 합. */
    @Column(name = "order_count", nullable = false)
    private long orderCount;

    /** 기간 내 조회수 합. */
    @Column(name = "view_count", nullable = false)
    private long viewCount;

    protected MvProductRank() {}

    protected MvProductRank(String periodKey, ProductRankScore row) {
        this.periodKey = periodKey;
        this.productId = row.productId();
        this.rankNo = null;
        this.score = row.score();
        this.likeCount = row.likeCount();
        this.orderCount = row.orderCount();
        this.viewCount = row.viewCount();
    }

    /** 기간에 맞는 MV 로우를 만든다. 일간은 Redis 실시간 랭킹이 담당하므로 MV 대상이 아니다. */
    public static MvProductRank of(RankingPeriod period, String periodKey, ProductRankScore row) {
        return switch (period) {
            case WEEKLY -> new MvProductRankWeekly(periodKey, row);
            case MONTHLY -> new MvProductRankMonthly(periodKey, row);
            case DAILY -> throw new IllegalArgumentException(
                "일간 랭킹은 MV 로 적재하지 않습니다(Redis 실시간 랭킹이 담당).");
        };
    }

    public String getPeriodKey() { return periodKey; }
    public Long getProductId() { return productId; }
    public Integer getRankNo() { return rankNo; }
    public double getScore() { return score; }
    public long getLikeCount() { return likeCount; }
    public long getOrderCount() { return orderCount; }
    public long getViewCount() { return viewCount; }
}
