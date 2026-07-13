package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthUser;
import com.loopers.interfaces.api.user.AuthUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 대고객 쿠폰 API.
 *
 * <p><strong>비동기 발급 (Round 7)</strong>: 발급은 접수(202 + requestId)만 하고 실제 발급은
 * Kafka 컨슈머가 수행한다. 결과는 {@code GET /api/v1/coupons/issue-requests/{requestId}} 폴링으로 확인.
 *
 * <p>발급/내 쿠폰 목록은 경로 prefix 가 다르므로 클래스 레벨 매핑 없이 메서드 레벨 전체 경로를 사용한다.
 */
@RequiredArgsConstructor
@RestController
public class CouponV1Controller {

    private final CouponApplicationService couponApplicationService;

    /** 발급 요청 접수 — 즉시 202. 선착순 판정은 컨슈머가 도착 순서대로 수행한다. */
    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CouponV1Dto.IssueAcceptedResponse> issue(
        @AuthUser AuthUserContext authUser,
        @PathVariable Long couponId
    ) {
        CouponIssueRequestInfo info = couponApplicationService.requestIssue(authUser.userId(), couponId);
        return ApiResponse.success(CouponV1Dto.IssueAcceptedResponse.from(info));
    }

    /** 발급 요청 결과 폴링 — PENDING(처리 중) / ISSUED(발급 완료) / FAILED(사유 포함). */
    @GetMapping("/api/v1/coupons/issue-requests/{requestId}")
    public ApiResponse<CouponV1Dto.IssueRequestResponse> getIssueRequest(
        @AuthUser AuthUserContext authUser,
        @PathVariable String requestId
    ) {
        CouponIssueRequestInfo info = couponApplicationService.getIssueRequest(authUser.userId(), requestId);
        return ApiResponse.success(CouponV1Dto.IssueRequestResponse.from(info));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(
        @AuthUser AuthUserContext authUser
    ) {
        List<CouponV1Dto.UserCouponResponse> responses = couponApplicationService.getMyCoupons(authUser.userId()).stream()
            .map(CouponV1Dto.UserCouponResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
