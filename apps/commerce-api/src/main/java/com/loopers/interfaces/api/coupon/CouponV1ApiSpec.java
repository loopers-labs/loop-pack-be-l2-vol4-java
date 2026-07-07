package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Coupon V1 API", description = "쿠폰 발급/조회 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "쿠폰 템플릿 ID로 헤더로 식별한 유저에게 쿠폰을 발급합니다. 발급 시점 정책이 스냅샷으로 저장됩니다.")
    ApiResponse<CouponV1Dto.UserCouponResponse> issueCoupon(AuthHeaders auth, Long couponId);

    @Operation(summary = "내 쿠폰 목록", description = "보유 쿠폰을 상태(AVAILABLE/USED/EXPIRED)와 함께 반환합니다.")
    ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(AuthHeaders auth);

    @Operation(summary = "선착순 쿠폰 발급요청", description = "선착순(quantity 설정) 쿠폰 발급을 비동기로 요청합니다. PENDING 결과가 저장되고 요청 이벤트가 발행되며, 처리 결과는 requestId 로 조회합니다.")
    ApiResponse<CouponV1Dto.IssueRequestResponse> requestIssue(AuthHeaders auth, Long couponId);

    @Operation(summary = "선착순 쿠폰 발급요청 결과 조회", description = "requestId 로 비동기 발급요청의 처리 결과(PENDING/ISSUED/REJECTED_SOLD_OUT/REJECTED_DUPLICATE)를 조회합니다.")
    ApiResponse<CouponV1Dto.IssueResultResponse> getIssueResult(String requestId);
}
