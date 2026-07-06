package com.loopers.interfaces.api.coupon;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Coupon V1 API", description = "Loopers 쿠폰 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(
        summary = "쿠폰 발급",
        description = "쿠폰 템플릿으로부터 사용자에게 쿠폰을 발급합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true, description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true, description = "비밀번호")
        }
    )
    ApiResponse<CouponV1Dto.IssueResponse> issue(
        @Parameter(hidden = true) User user,
        @Parameter(description = "쿠폰 템플릿 ID") Long couponId
    );

    @Operation(
        summary = "선착순 쿠폰 발급 요청",
        description = "발급 요청을 접수하고 requestId 를 즉시 반환합니다. 실제 발급은 비동기(Kafka) 처리되며 결과는 폴링으로 확인합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true, description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true, description = "비밀번호")
        }
    )
    ApiResponse<CouponV1Dto.IssueRequestResponse> requestIssue(
        @Parameter(hidden = true) User user,
        @Parameter(description = "쿠폰 템플릿 ID") Long couponId
    );

    @Operation(
        summary = "선착순 쿠폰 발급 결과 조회",
        description = "requestId 로 발급 처리 결과(PENDING/ISSUED/REJECTED)를 조회합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true, description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true, description = "비밀번호")
        }
    )
    ApiResponse<CouponV1Dto.IssueRequestResponse> getIssueRequest(
        @Parameter(hidden = true) User user,
        @Parameter(description = "발급 요청 ID") String requestId
    );

    @Operation(
        summary = "내 쿠폰 목록 조회",
        description = "내가 발급받은 쿠폰 목록을 사용 가능(AVAILABLE)/사용 완료(USED)/만료(EXPIRED) 상태와 함께 조회합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true, description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true, description = "비밀번호")
        }
    )
    ApiResponse<List<CouponV1Dto.MyCouponResponse>> getMyCoupons(
        @Parameter(hidden = true) User user
    );
}
