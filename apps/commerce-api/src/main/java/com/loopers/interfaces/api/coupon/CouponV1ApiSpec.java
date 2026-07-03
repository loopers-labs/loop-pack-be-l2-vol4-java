package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Coupon", description = "쿠폰 API")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "인증된 사용자가 쿠폰을 발급받는다.")
    ApiResponse<CouponV1Dto.IssueResponse> issueCoupon(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @PathVariable Long couponId
    );

    @Operation(summary = "선착순 쿠폰 발급 요청", description = "발급 요청을 큐에 접수하고 즉시 응답한다. 실제 발급은 비동기로 처리된다.")
    ApiResponse<CouponV1Dto.IssueRequestResponse> requestIssueCoupon(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @PathVariable Long couponId
    );

    @Operation(summary = "발급 요청 상태 조회", description = "requestId로 발급 처리 상태(PENDING/ISSUED/SOLD_OUT)를 조회한다.")
    ApiResponse<CouponV1Dto.IssueRequestStatusResponse> getIssueRequestStatus(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @PathVariable String requestId
    );
}
