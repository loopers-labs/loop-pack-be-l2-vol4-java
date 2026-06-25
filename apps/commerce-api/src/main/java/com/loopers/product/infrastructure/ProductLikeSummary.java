package com.loopers.product.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 좋아요 수 읽기모델(projection). 원본은 likes 테이블이며, 이 테이블은 조회 정렬을 빠르게 하기 위한
 * 가속 사본이다. brand_id 는 "브랜드 필터 + 좋아요순"을 단일 인덱스로 처리하기 위해 비정규화한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_like_summary")
public class ProductLikeSummary {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;
}
