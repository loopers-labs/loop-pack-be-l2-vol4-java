package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

/** 선착순 쿠폰 발급 요청 저장소. */
public interface CouponIssueRequestRepository {

    CouponIssueRequest save(CouponIssueRequest request);

    Optional<CouponIssueRequest> findByRequestId(String requestId);

    /** 배치 처리용 일괄 조회 — 반환 순서는 보장하지 않으므로 호출측이 도착 순서로 재정렬한다. */
    List<CouponIssueRequest> findByRequestIdIn(List<String> requestIds);
}
