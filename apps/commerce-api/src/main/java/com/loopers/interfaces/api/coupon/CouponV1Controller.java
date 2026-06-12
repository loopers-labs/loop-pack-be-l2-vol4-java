package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ApiResponse<CouponV1Dto.CouponResponse> issue(
            @RequestHeader(value = "X-Loopers-UserId", required = false) Long userId,
            @PathVariable Long couponId
    ) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-Loopers-UserId 헤더가 필요합니다.");
        }
        return ApiResponse.success(
                CouponV1Dto.CouponResponse.from(couponFacade.issue(userId, couponId)));
    }

    @GetMapping("/users/me/coupons")
    @Override
    public ApiResponse<List<CouponV1Dto.CouponResponse>> getMyCoupons(
            @RequestHeader(value = "X-Loopers-UserId", required = false) Long userId
    ) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-Loopers-UserId 헤더가 필요합니다.");
        }
        List<CouponV1Dto.CouponResponse> coupons = couponFacade.getMyCoupons(userId).stream()
                .map(CouponV1Dto.CouponResponse::from)
                .toList();
        return ApiResponse.success(coupons);
    }
}
