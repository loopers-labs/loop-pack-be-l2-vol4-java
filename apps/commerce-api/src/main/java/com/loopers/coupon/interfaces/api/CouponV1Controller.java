package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.CouponIssueRequestService;
import com.loopers.coupon.application.CouponIssueService;
import com.loopers.coupon.application.CouponQueryService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponIssueService couponIssueService;
    private final CouponQueryService couponQueryService;
    private final CouponIssueRequestService couponIssueRequestService;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @Override
    public ApiResponse<CouponV1Response.IssueDetail> issue(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long couponId
    ) {
        return ApiResponse.success(CouponV1Response.IssueDetail.from(couponIssueService.issue(userId, couponId)));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<CouponV1Response.MyCoupons> getMyCoupons(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(CouponV1Response.MyCoupons.from(couponQueryService.getMyCoupons(userId)));
    }

    /** 선착순 발급 요청 접수 — Kafka 로 발행만 하고 즉시 requestId 를 반환한다(비동기). */
    @PostMapping("/api/v1/coupons/{couponId}/issue-requests")
    public ApiResponse<CouponV1Response.IssueRequestAccepted> requestIssue(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long couponId
    ) {
        String requestId = couponIssueRequestService.request(userId, couponId);
        return ApiResponse.success(new CouponV1Response.IssueRequestAccepted(requestId));
    }

    /** 선착순 발급 결과 조회(polling) — PENDING|SUCCESS|REJECTED */
    @GetMapping("/api/v1/coupons/issue-requests/{requestId}")
    public ApiResponse<CouponV1Response.IssueRequestResult> getIssueResult(@PathVariable String requestId) {
        return ApiResponse.success(CouponV1Response.IssueRequestResult.from(couponIssueRequestService.getResult(requestId)));
    }
}
