package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon Admin API", description = "어드민 쿠폰 정책 API")
public interface CouponAdminV1ApiSpec {

    @Operation(
        summary = "쿠폰 정책 등록",
        description = "신규 쿠폰 정책(템플릿)을 등록합니다. 할인 값이 타입 규칙에 맞지 않으면 400 INVALID_COUPON_VALUE 를 반환합니다."
    )
    ApiResponse<CouponAdminV1Dto.Response> createPolicy(CouponAdminV1Dto.CreateRequest request);

    @Operation(
        summary = "쿠폰 정책 목록 조회",
        description = "삭제된 정책을 포함한 전체 쿠폰 정책을 최신순으로 페이지 단위 조회합니다. 기본 page=0, size=20."
    )
    ApiResponse<CouponAdminV1Dto.PageResponse> getPolicies(int page, int size);

    @Operation(
        summary = "쿠폰 정책 단건 조회",
        description = "삭제된 정책도 조회할 수 있습니다. 존재하지 않으면 404 COUPON_POLICY_NOT_FOUND 를 반환합니다."
    )
    ApiResponse<CouponAdminV1Dto.Response> getPolicy(Long couponPolicyId);

    @Operation(
        summary = "쿠폰 정책 수정",
        description = "이름·최소주문금액·만료일(메타 정보)을 수정합니다. 타입·할인값은 불변입니다. "
            + "삭제되었거나 존재하지 않는 정책이면 404 COUPON_POLICY_NOT_FOUND 를 반환합니다."
    )
    ApiResponse<CouponAdminV1Dto.Response> updatePolicy(Long couponPolicyId, CouponAdminV1Dto.UpdateRequest request);

    @Operation(
        summary = "쿠폰 정책 삭제",
        description = "쿠폰 정책을 soft-delete 합니다. 발급된 사용자 쿠폰 존재 여부와 무관하게 삭제되며, 이미 발급된 쿠폰은 그대로 유효합니다. "
            + "존재하지 않는 정책이면 404 COUPON_POLICY_NOT_FOUND 를 반환합니다."
    )
    ApiResponse<Void> deletePolicy(Long couponPolicyId);

    @Operation(
        summary = "쿠폰 정책 발급 내역 조회",
        description = "해당 정책으로 발급된 사용자 쿠폰을 사용자 ID·표시 상태(AVAILABLE/USED/EXPIRED)와 함께 페이지 단위로 조회합니다. "
            + "존재하지 않는 정책이면 404 COUPON_POLICY_NOT_FOUND 를 반환합니다."
    )
    ApiResponse<CouponAdminV1Dto.IssuedCouponPageResponse> getIssuedCoupons(Long couponPolicyId, int page, int size);
}
