package com.loopers.interfaces.api.user;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.user.UserApplicationService;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.interfaces.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserApplicationService userApplicationService;
    private final CouponApplicationService couponApplicationService;

    @PostMapping
    public ApiResponse<Object> signup(
        @RequestBody UserV1Dto.SignupRequest request
    ) {
        userApplicationService.signup(
            request.userId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(
        @RequestHeader("X-Loopers-LoginId") String userId,
        @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        UserInfo user = userApplicationService.getUser(userId, password);
        return ApiResponse.success(UserV1Dto.UserResponse.from(user));
    }

    @GetMapping("/me/coupons")
    public ApiResponse<PageResult<CouponV1Dto.MyCouponResponse>> getMyCoupons(
            @LoginUser Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(PageResult.from(
                couponApplicationService.getMyCoupons(userId, PageRequest.of(page, size))
                        .map(CouponV1Dto.MyCouponResponse::from)
        ));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Object> changePassword(
        @RequestHeader("X-Loopers-LoginId") String userId,
        @RequestHeader("X-Loopers-LoginPw") String password, // 헤더 필수 검증용, 비즈니스 로직 미사용
        @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userApplicationService.changePassword(
            userId,
            request.currentPassword(),
            request.newPassword()
        );
        return ApiResponse.success();
    }
}
