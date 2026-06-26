package com.loopers.domain.order;

import com.loopers.domain.common.PageResult;

import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> find(Long id);

    /**
     * 비관적 쓰기 락(FOR UPDATE)으로 주문을 조회한다. 결제 예약 시 같은 주문에 대한
     * 동시 결제 시도를 직렬화하기 위한 조정점으로 사용한다(락은 짧게, PG 호출 전에 해제).
     */
    Optional<Order> findForUpdate(Long id);

    PageResult<Order> findAllByUser(OrderCommand.MySearch search);

    PageResult<Order> findAll(OrderCommand.AdminSearch search);
}
