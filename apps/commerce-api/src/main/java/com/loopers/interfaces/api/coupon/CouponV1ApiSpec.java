package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Coupon V1 API", description = "Loopers 쿠폰 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급 요청", description = "쿠폰 템플릿 ID로 발급을 요청한다. 실제 발급은 비동기로 처리되며 requestId로 결과를 조회한다.")
    ApiResponse<CouponV1Dto.IssueRequestResponse> requestIssue(
            @RequestHeader String loginId,
            @RequestHeader String loginPw,
            Long couponId
    );

    @Operation(summary = "쿠폰 발급 요청 상태 조회", description = "requestId로 발급 요청의 처리 상태(PENDING/ISSUED/FAILED)를 조회한다.")
    ApiResponse<CouponV1Dto.IssueRequestStatusResponse> getIssueRequestStatus(
            @RequestHeader String loginId,
            @RequestHeader String loginPw,
            String requestId
    );
}
