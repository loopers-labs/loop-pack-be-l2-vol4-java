package com.loopers.domain.coupon;

import java.util.Optional;

/** 선착순 발급 요청 저장소 포트 (Slice 4). 순수 도메인 ↔ JPA 경계는 infrastructure 구현이 담당. */
public interface CouponIssueRequestRepository {

    CouponIssueRequestModel save(CouponIssueRequestModel request);

    Optional<CouponIssueRequestModel> find(Long requestId);

    /** 1인 1매 멱등 — 같은 사용자·쿠폰의 기존 요청 조회(UNIQUE(user_id, coupon_id) 기반). */
    Optional<CouponIssueRequestModel> findByUserIdAndCouponId(Long userId, Long couponId);
}
