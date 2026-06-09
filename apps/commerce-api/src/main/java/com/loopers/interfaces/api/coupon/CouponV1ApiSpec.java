package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Coupon V1 API", description = "Loopers 쿠폰 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급 요청", description = "쿠폰 템플릿 ID로 쿠폰을 발급합니다.")
    ApiResponse<CouponV1Dto.IssueResponse> issue(
            @RequestHeader String loginId,
            @RequestHeader String loginPw,
            Long couponId
    );
}
