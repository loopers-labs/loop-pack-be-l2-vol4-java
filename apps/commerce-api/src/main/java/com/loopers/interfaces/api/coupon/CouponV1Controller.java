package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
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
}
