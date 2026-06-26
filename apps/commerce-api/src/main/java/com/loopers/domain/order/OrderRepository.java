package com.loopers.domain.order;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> findById(Long id);
    List<OrderModel> findByUserIdAndOrderedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);
    List<OrderModel> findAll(int page, int size);

    /**
     * 비관적 쓰기 락으로 주문을 조회한다.
     *
     * <p>같은 주문에 대한 중복 confirm, confirm vs 만료 스케줄러 경합을 직렬화하는 용도 —
     * 한 트랜잭션만 상태 전이에 성공하고 나머지는 상태 머신 가드에서 거부된다.
     */
    Optional<OrderModel> findByIdForUpdate(Long id);

    /** 특정 시각 이전에 생성된 특정 상태의 주문 목록. 오래된 PENDING 주문 만료 처리용. */
    List<OrderModel> findByStatusAndOrderedAtBefore(OrderStatus status, ZonedDateTime before);

    /** 특정 상태의 주문 전체. PG 대사 스케줄러가 PAYMENT_IN_PROGRESS 건을 매 틱 조회하는 용도. */
    List<OrderModel> findByStatus(OrderStatus status);
}
