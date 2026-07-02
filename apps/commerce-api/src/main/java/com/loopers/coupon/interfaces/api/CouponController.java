package com.loopers.coupon.interfaces.api;

import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.coupon.application.CouponFacade;
import com.loopers.member.application.MemberFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class CouponController {

    private final CouponFacade couponFacade;
    private final MemberFacade memberFacade;

    @PostMapping("/coupons/{couponId}/issue")
    public ApiResponse<MyCouponResponse> issue(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @PathVariable("couponId") Long couponId) {
        Long memberId = memberFacade.authenticate(loginId, loginPw);
        return ApiResponse.success(MyCouponResponse.from(couponFacade.issueCoupon(memberId, couponId)));
    }

    @GetMapping("/users/me/coupons")
    public ApiResponse<List<MyCouponResponse>> getMyCoupons(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw) {
        Long memberId = memberFacade.authenticate(loginId, loginPw);
        return ApiResponse.success(
            couponFacade.getMyCoupons(memberId).stream().map(MyCouponResponse::from).toList());
    }
}
