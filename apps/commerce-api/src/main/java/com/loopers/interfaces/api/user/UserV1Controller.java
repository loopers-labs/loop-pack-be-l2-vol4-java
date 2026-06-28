package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserFacade userFacade;

    @PostMapping
    public ApiResponse<UserV1Dto.UserResponse> register(
        @RequestBody UserV1Dto.RegisterRequest request
    ) {
        UserInfo info = userFacade.register(
            request.loginId(), request.loginPw(), request.email(), request.nickname(), request.birthDate()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getMe(
        @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
        @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw
    ) {
        UserInfo info = userFacade.login(loginId, loginPw);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @PutMapping("/password")
    public ApiResponse<UserV1Dto.UserResponse> changePassword(
        @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
        @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
        @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        UserInfo info = userFacade.changePassword(loginId, loginPw, request.newPassword());
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }
}
