package com.loopers.ranking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * MV 의 복합키. 여기 선언한 순서가 PK 컬럼 순서가 된다 — period_key 가 앞이어야
 * InnoDB 클러스터드 인덱스에서 한 기간의 행이 연속으로 모인다.
 * (@IdClass 는 MappedSuperclass 상속과 겹치면 순서를 보장하지 않아 @EmbeddedId 를 쓴다)
 */
@Embeddable
@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRankId implements Serializable {

    @Column(name = "period_key", length = 16, nullable = false)
    private String periodKey;

    @Column(name = "product_id", nullable = false)
    private Long productId;
}
