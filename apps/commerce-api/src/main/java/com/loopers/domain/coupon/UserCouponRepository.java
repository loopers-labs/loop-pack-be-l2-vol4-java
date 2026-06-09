package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCouponRepository {
    UserCouponModel save(UserCouponModel userCoupon);

    Optional<UserCouponModel> find(UUID id);

    /** 소유권 검증용 — id + userId 단건 조회 */
    Optional<UserCouponModel> findByIdAndUserId(UUID id, UUID userId);

    /** 동일 템플릿 중복 발급 검사 */
    boolean existsByUserIdAndTemplateId(UUID userId, UUID templateId);

    /** 내 쿠폰 목록 */
    List<UserCouponModel> findByUserId(UUID userId);

    /** 템플릿별 발급 내역 (어드민) */
    Page<UserCouponModel> findByTemplateId(UUID templateId, Pageable pageable);
}
