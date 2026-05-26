package com.loopers.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> find(Long id);

    /** 특정 사용자의 주문을 최신순(id DESC)으로 페이지 조회 (UC-09). */
    List<OrderModel> findByUserId(Long userId, int page, int size);
}
