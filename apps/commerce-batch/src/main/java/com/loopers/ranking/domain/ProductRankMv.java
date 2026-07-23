package com.loopers.ranking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 기간별 상위 150 랭킹의 확정본 중 주간·월간이 공유하는 값. 순위는 저장하지 않는다 —
 * 조회가 인덱스 순서대로 읽으며 매긴다.
 * 식별 필드(period_key, product_id)는 여기 두지 않고 각 엔티티가 선언한다.
 * MappedSuperclass 에 두면 Hibernate 가 PK 컬럼 순서를 뒤집어 (product_id, period_key) 로 만들기 때문이다.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ProductRankMv {

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    protected ProductRankMv(double score, long viewCount, long likeCount, long salesCount, ZonedDateTime createdAt) {
        this.score = score;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.createdAt = createdAt;
    }
}
