package com.loopers.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> find(Long id);

    /** 특정 사용자의 주문을 최신순(id DESC)으로 페이지 조회 (UC-09). */
    List<OrderModel> findByUserId(Long userId, int page, int size);

    /** 전체 주문을 최신순으로 페이지 조회 — status가 null이면 전체, 아니면 상태 필터 (UC-12 Admin). */
    List<OrderModel> findAll(OrderStatus status, int page, int size);
}
