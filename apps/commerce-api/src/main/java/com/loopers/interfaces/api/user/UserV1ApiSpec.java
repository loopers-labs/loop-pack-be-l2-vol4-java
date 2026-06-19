package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@Tag(name = "User", description = "유저 API")
public interface UserV1ApiSpec {

    @Operation(summary = "유저 생성", description = "loginId/loginPw로 유저를 생성한다.")
    ApiResponse<UserV1Dto.UserResponse> createUser(@RequestBody UserV1Dto.CreateUserRequest request);

    @Operation(summary = "내 쿠폰 목록 조회", description = "인증된 사용자의 쿠폰 목록을 상태와 함께 반환한다.")
    ApiResponse<List<UserV1Dto.CouponResponse>> getMyCoupons(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    );
}
