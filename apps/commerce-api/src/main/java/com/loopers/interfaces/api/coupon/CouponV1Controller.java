package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthUser;
import com.loopers.interfaces.api.user.AuthUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 대고객 쿠폰 API.
 *
 * <p>발급(/api/v1/coupons/{couponId}/issue)과 내 쿠폰 목록(/api/v1/users/me/coupons)은
 * 경로 prefix 가 다르므로 클래스 레벨 매핑 없이 메서드 레벨 전체 경로를 사용한다.
 */
@RequiredArgsConstructor
@RestController
public class CouponV1Controller {

    private final CouponApplicationService couponApplicationService;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponV1Dto.UserCouponResponse> issue(
        @AuthUser AuthUserContext authUser,
        @PathVariable Long couponId
    ) {
        UserCouponInfo info = couponApplicationService.issue(authUser.userId(), couponId);
        return ApiResponse.success(CouponV1Dto.UserCouponResponse.from(info));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(
        @AuthUser AuthUserContext authUser
    ) {
        List<CouponV1Dto.UserCouponResponse> responses = couponApplicationService.getMyCoupons(authUser.userId()).stream()
            .map(CouponV1Dto.UserCouponResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
