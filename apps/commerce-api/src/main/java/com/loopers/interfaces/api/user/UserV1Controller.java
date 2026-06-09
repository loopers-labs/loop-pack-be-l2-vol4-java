package com.loopers.interfaces.api.user;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;
    private final CouponFacade couponFacade;

    @PostMapping
    @Override
    public ApiResponse<UserV1Dto.UserResponse> createUser(
        @RequestBody UserV1Dto.CreateUserRequest request
    ) {
        UserInfo info = userFacade.createUser(request.loginId(), request.loginPw());
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @GetMapping("/me/coupons")
    @Override
    public ApiResponse<List<UserV1Dto.CouponResponse>> getMyCoupons(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        List<UserCouponInfo> infos = couponFacade.getMyCoupons(loginId, loginPw);
        return ApiResponse.success(infos.stream().map(UserV1Dto.CouponResponse::from).toList());
    }
}
