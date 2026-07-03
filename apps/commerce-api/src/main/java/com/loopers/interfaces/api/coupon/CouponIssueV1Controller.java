package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueRequestCriteria;
import com.loopers.application.coupon.CouponIssueRequestFacade;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponIssueV1Controller {

    private final CouponIssueRequestFacade facade;

    /**
     * 선착순 쿠폰 발급 요청 접수 — 실제 처리는 Kafka Consumer 가.
     * 응답: 202 + requestId. 사용자는 GET 으로 결과를 폴링한다.
     */
    @PostMapping("/{couponTemplateId}/issue-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CouponV1Dto.IssueRequestResponse> enqueue(
        @PathVariable("couponTemplateId") Long couponTemplateId,
        @RequestHeader("X-Loopers-User-Id") Long userId
    ) {
        CouponIssueRequestInfo info = facade.enqueue(
            new CouponIssueRequestCriteria.Enqueue(userId, couponTemplateId));
        return ApiResponse.success(CouponV1Dto.IssueRequestResponse.from(info));
    }

    @GetMapping("/issue-requests/{requestId}")
    public ApiResponse<CouponV1Dto.IssueRequestResponse> getStatus(
        @PathVariable("requestId") String requestId
    ) {
        CouponIssueRequestInfo info = facade.getStatus(requestId);
        return ApiResponse.success(CouponV1Dto.IssueRequestResponse.from(info));
    }
}
