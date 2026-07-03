package com.loopers.metrics.ssot;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매량 재계산의 SSOT — commerce-api order_items 테이블의 read-only 뷰(필요 컬럼만).
 * 재계산 쿼리가 이 테이블을 읽으므로 streamer 스키마에도 존재해야 한다(공유 DB).
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;
}
