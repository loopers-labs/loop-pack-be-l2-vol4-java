package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 대고객 쿠폰 API — 사용자 식별은 X-Loopers-LoginId/LoginPw 헤더 인증으로 처리.
 * 발급 요청의 {couponId}는 쿠폰 템플릿 식별자다(01 §5.3).
 */
@RequiredArgsConstructor
@RestController
public class CouponV1Controller {

    private final CouponFacade couponFacade;
    private final UserFacade userFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.IssuedCouponResponse> issue(
        @PathVariable(value = "couponId") Long couponId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        return ApiResponse.success(CouponV1Dto.IssuedCouponResponse.from(couponFacade.issue(userId, couponId)));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<List<CouponV1Dto.IssuedCouponResponse>> getMyCoupons(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        List<CouponV1Dto.IssuedCouponResponse> responses = couponFacade.getMyCoupons(userId, page, size).stream()
            .map(CouponV1Dto.IssuedCouponResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    /**
     * 선착순 쿠폰 발급 요청 (Slice 4). 접수만 하고 즉시 202 Accepted — 실제 발급은 비동기로 확정된다.
     * 같은 사용자·쿠폰 재요청은 멱등(기존 requestId 반환). 결과는 {@code GET .../issue-requests/{requestId}}로 조회.
     */
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/api/v1/coupons/{couponId}/issue-requests")
    public ApiResponse<CouponV1Dto.IssueRequestAcceptedResponse> requestIssue(
        @PathVariable(value = "couponId") Long couponId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        return ApiResponse.success(
            CouponV1Dto.IssueRequestAcceptedResponse.from(couponFacade.requestIssue(userId, couponId)));
    }

    /** 선착순 발급 요청 결과 조회 (Slice 4) — PENDING/ISSUED/SOLD_OUT/REJECTED. */
    @GetMapping("/api/v1/coupons/issue-requests/{requestId}")
    public ApiResponse<CouponV1Dto.IssueResultResponse> getIssueResult(
        @PathVariable(value = "requestId") Long requestId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        userFacade.authenticate(loginId, loginPw); // 인증만 — 결과는 requestId로 조회
        return ApiResponse.success(
            CouponV1Dto.IssueResultResponse.from(couponFacade.getIssueResult(requestId)));
    }
}
