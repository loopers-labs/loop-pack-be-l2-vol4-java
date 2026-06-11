package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCouponRepository {
    UserCouponModel save(UserCouponModel userCoupon);

    Optional<UserCouponModel> find(UUID id);

    /** 소유권 검증용 — id + userId 단건 조회 */
    Optional<UserCouponModel> findByIdAndUserId(UUID id, UUID userId);

    /** 내 쿠폰 목록 */
    List<UserCouponModel> findByUserId(UUID userId);

    /** 템플릿별 발급 내역 (어드민) */
    Page<UserCouponModel> findByTemplateId(UUID templateId, Pageable pageable);

    /** 조건부 사용 — AVAILABLE 일 때만 USED 전이. 반환=영향 행 수(0이면 이미 사용) */
    int useIfAvailable(UUID id, UUID orderId, ZonedDateTime usedAt);

    /** 사용 취소 — 단건 주문 기준 USED → AVAILABLE */
    int releaseByOrderId(UUID orderId);

    /** 사용 취소 — 복수 주문 기준 (배치) */
    int releaseByOrderIds(List<UUID> orderIds);
}
