package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping("/coupons/{couponId}/issue")
    public ApiResponse<CouponDto.Issued.Response> issueCoupon(
        @LoginUser AuthenticatedUser user,
        @PathVariable(value = "couponId") Long couponId
    ) {
        CouponDto.Issued.Response response = CouponDto.Issued.Response.from(
            couponFacade.issueCoupon(couponId, user.loginId(), ZonedDateTime.now())
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/users/me/coupons")
    public ApiResponse<List<CouponDto.Issued.Response>> getMyCoupons(
        @LoginUser AuthenticatedUser user
    ) {
        List<CouponDto.Issued.Response> responses = couponFacade.getMyCoupons(user.loginId(), ZonedDateTime.now()).stream()
            .map(CouponDto.Issued.Response::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
