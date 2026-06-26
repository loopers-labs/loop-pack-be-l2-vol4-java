package com.loopers.domain.order;

import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> find(Long id);

    /** 비관적 락(FOR UPDATE)으로 주문을 잠가 조회 — 동시 결제 접수를 직렬화해 활성 결제 중복 생성을 막는다. */
    Optional<Order> findForUpdate(Long id);
}
