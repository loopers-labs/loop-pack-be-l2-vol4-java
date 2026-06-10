package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * OrderItem 자체는 외부에 노출하지 않는다 (Order 애그리거트 내부 구성요소).
 * 본 리포지토리는 통합 테스트에서 행 카운트 등 기반 검증용으로만 사용.
 */
public interface OrderItemJpaRepository extends JpaRepository<OrderItem, Long> {
}
