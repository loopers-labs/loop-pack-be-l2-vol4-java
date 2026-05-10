package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserFacade userFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserV1Dto.UserResponse> register(
        @RequestBody UserV1Dto.RegisterRequest request
    ) {
        UserInfo info = userFacade.signUp(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getMe(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        UserInfo info = userFacade.getUser(loginId, loginPw);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.updatePassword(loginId, request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
