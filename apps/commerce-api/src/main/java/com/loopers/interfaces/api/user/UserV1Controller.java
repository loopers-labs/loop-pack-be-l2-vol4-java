package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping
    @Override
    public ApiResponse<UserV1Dto.UserResponse> register(@RequestBody UserV1Dto.RegisterRequest request) {
        UserInfo info = userFacade.registerUser(
            request.userid(),
            request.password(),
            request.name(),
            request.birthDay(),
            request.email()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> getUser(@RequestHeader("X-Loopers-LoginId") String loginId) {
        UserInfo info = userFacade.getUser(loginId);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @PatchMapping("/password")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> changePassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestBody UserV1Dto.ChangePasswordRequest request) {
        UserInfo info = userFacade.changePassword(loginId, request.newPassword());
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }
}
