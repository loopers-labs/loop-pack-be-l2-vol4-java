package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    OrderModel save(OrderModel order);

    Optional<OrderModel> findById(UUID id);

    /** 유저 주문 목록 — 기간 필터, 페이징 */
    Page<OrderModel> findAllByUserId(UUID userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable);

    /** 어드민 전체 주문 목록 */
    Page<OrderModel> findAll(Pageable pageable);

    /** 스케줄러용 — createdAt 기준 특정 시각 이전 PENDING 주문 */
    List<OrderModel> findPendingBefore(ZonedDateTime before);
}
