package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 API", description = "Loopers 쿠폰 대고객 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(
            summary = "쿠폰 발급",
            description = "쿠폰 템플릿(couponId)으로 로그인 사용자의 쿠폰을 발급합니다. 한 사용자는 같은 템플릿을 1장만 가질 수 있습니다."
    )
    ApiResponse<CouponV1Dto.IssuedResponse> issue(Long userId, Long couponId);

    @Operation(
            summary = "내 쿠폰 목록 조회",
            description = "로그인 사용자가 보유한 쿠폰을 상태(AVAILABLE/USED/EXPIRED)와 함께 페이징하여 반환합니다."
    )
    ApiResponse<CouponV1Dto.MyCouponPageResponse> getMyCoupons(Long userId, int page, int size);

    @Operation(
            summary = "선착순 쿠폰 발급 요청",
            description = "한도가 있는(선착순) 쿠폰 템플릿의 발급을 요청합니다. 요청을 접수하고 requestId 를 즉시 반환하며, "
                    + "실제 발급은 비동기로 처리됩니다. 결과는 requestId 로 폴링하세요."
    )
    ApiResponse<CouponV1Dto.IssueRequestAccepted> requestIssue(Long userId, Long templateId);

    @Operation(
            summary = "선착순 발급 요청 상태 조회(폴링)",
            description = "requestId 로 <b>로그인 사용자 본인</b>의 발급 요청 처리 상태를 조회합니다. "
                    + "(PENDING/SUCCESS/SOLD_OUT/ALREADY_ISSUED/FAILED)"
    )
    ApiResponse<CouponV1Dto.IssueRequestStatusResponse> getIssueRequest(Long userId, String requestId);
}
