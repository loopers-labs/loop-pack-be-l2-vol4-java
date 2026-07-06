package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품별 집계(좋아요 수·판매량·조회 수). 이벤트를 원천으로 upsert 로만 갱신되는 파생 데이터다.
 * 실제 갱신은 동시성 안전을 위해 리포지토리의 단문 upsert(INSERT ... ON DUPLICATE KEY UPDATE)로 처리하고,
 * 이 엔티티는 스키마 정의와 조회 용도로만 쓴다.
 */
@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics {

    @Id
    private Long productId;

    @Column(nullable = false)
    private Long likeCount;

    @Column(nullable = false)
    private Long saleCount;

    @Column(nullable = false)
    private Long viewCount;
}
