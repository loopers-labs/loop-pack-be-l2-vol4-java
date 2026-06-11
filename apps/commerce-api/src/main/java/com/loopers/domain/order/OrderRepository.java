package com.loopers.domain.order;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> findById(Long id);
    List<OrderModel> findByUserIdAndOrderedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);
    List<OrderModel> findAll(int page, int size);

    /** 특정 시각 이전에 생성된 특정 상태의 주문 목록. 오래된 PENDING 주문 만료 처리용. */
    List<OrderModel> findByStatusAndOrderedAtBefore(OrderStatus status, ZonedDateTime before);
}
