package com.loopers.domain.order;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> find(Long id);

    List<Order> findByUserId(Long userId);

    /**
     * 유저의 주문 중 [start, end] 기간(inclusive)에 생성된 것만 조회한다.
     */
    List<Order> findByUserIdAndPeriod(Long userId, ZonedDateTime start, ZonedDateTime end);
}
