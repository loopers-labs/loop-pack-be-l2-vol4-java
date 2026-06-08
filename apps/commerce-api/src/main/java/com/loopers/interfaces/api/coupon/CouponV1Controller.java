package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponFacade couponFacade;

    @PostMapping("/coupons/{couponId}/issue")
    @Override
    public ApiResponse<CouponV1Dto.IssueResponse> issue(
        @LoginUser User user,
        @PathVariable("couponId") Long couponId
    ) {
        return ApiResponse.success(CouponV1Dto.IssueResponse.from(couponFacade.issue(user.getId(), couponId)));
    }

    @GetMapping("/users/me/coupons")
    @Override
    public ApiResponse<List<CouponV1Dto.MyCouponResponse>> getMyCoupons(
        @LoginUser User user
    ) {
        List<CouponV1Dto.MyCouponResponse> responses = couponFacade.getMyCoupons(user.getId()).stream()
            .map(CouponV1Dto.MyCouponResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
