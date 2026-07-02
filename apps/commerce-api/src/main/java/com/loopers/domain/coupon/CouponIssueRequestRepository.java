package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueRequestRepository {

    CouponIssueRequest save(CouponIssueRequest request);

    /** 폴링·멱등 판정의 조회 키. requestId 는 전역 유일하다. */
    Optional<CouponIssueRequest> findByRequestId(String requestId);

}
