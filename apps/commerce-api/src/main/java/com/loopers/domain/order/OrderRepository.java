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

    /** 소유자 일치 조회 — 주문자가 아니면 empty */
    Optional<OrderModel> findByIdAndUserId(UUID id, UUID userId);

    /** 유저 주문 목록 — 기간 필터, 페이징 */
    Page<OrderModel> findAllByUserId(UUID userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable);

    /** 어드민 전체 주문 목록 */
    Page<OrderModel> findAll(Pageable pageable);

    /** 스케줄러용 — createdAt 기준 특정 시각 이전 PENDING 주문 */
    List<OrderModel> findPendingBefore(ZonedDateTime before);

    /** 스케줄러 배치용 — 아이템 fetch join 포함 (N+1 방지) */
    List<OrderModel> findPendingBeforeWithItems(ZonedDateTime before);

    /** 스케줄러 배치 상태 변경 — 단일 UPDATE */
    int failAllByIds(List<UUID> orderIds, ZonedDateTime now);
}
