package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Coupon API", description = "쿠폰 API")
public interface CouponV1ApiSpec {

    @Operation(
        summary = "쿠폰 발급",
        description = "로그인한 회원이 쿠폰 정책으로부터 사용자 쿠폰을 발급받습니다. 만료된 정책은 발급할 수 없습니다."
    )
    ApiResponse<CouponV1Dto.Response> issue(LoginUser loginUser, Long couponId);

    @Operation(
        summary = "선착순 쿠폰 발급 요청 (비동기)",
        description = "로그인한 회원이 선착순 쿠폰 발급을 비동기로 요청합니다. 요청은 즉시 PENDING 으로 접수되어 Kafka 로 발행되고, "
            + "실제 발급(수량 차감·쿠폰 생성)은 컨슈머가 수행합니다. 반환된 requestId 로 처리 상태를 조회할 수 있습니다."
    )
    ApiResponse<CouponIssueRequestV1Dto.Response> requestIssue(LoginUser loginUser, Long couponId);

    @Operation(
        summary = "선착순 쿠폰 발급 요청 상태 조회 (폴링)",
        description = "requestId 로 발급 요청의 처리 상태(PENDING/ISSUED/REJECTED)를 조회합니다. 본인 요청만 조회할 수 있습니다."
    )
    ApiResponse<CouponIssueRequestV1Dto.Response> getIssueRequest(LoginUser loginUser, Long requestId);

    @Operation(
        summary = "내 쿠폰 목록 조회",
        description = "로그인한 회원이 보유한 쿠폰 목록을 조회합니다. 표시 상태(AVAILABLE/USED/EXPIRED)는 조회 시점에 파생됩니다."
    )
    ApiResponse<List<CouponV1Dto.Response>> getMyCoupons(LoginUser loginUser);
}
