package com.loopers.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> find(Long id);

    /**
     * 결제 결과 반영 전용 — 주문 행을 비관적 락으로 잠그고 조회한다.
     * 동시 확정(reconcile/콜백)을 직렬화해 상태 전이·보상이 정확히 한 번만 일어나게 한다.
     */
    Optional<OrderModel> findForUpdate(Long id);

    /** 특정 사용자의 주문을 최신순(id DESC)으로 페이지 조회 (UC-09). */
    List<OrderModel> findByUserId(Long userId, int page, int size);

    /** 전체 주문을 최신순으로 페이지 조회 — status가 null이면 전체, 아니면 상태 필터 (UC-12 Admin). */
    List<OrderModel> findAll(OrderStatus status, int page, int size);
}
