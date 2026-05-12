package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<UserV1Dto.UserResponse> signUp(
        @RequestBody UserV1Dto.SignUpRequest request
    ) {
        UserInfo info = userFacade.signUp(request.toCommand());
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(
        @AuthenticationPrincipal Long userId
    ) {
        UserInfo info = userFacade.getMyInfo(userId);
        return ApiResponse.success(UserV1Dto.MyInfoResponse.from(info));
    }

    @PutMapping("/me/password")
    @Override
    public ApiResponse<Object> changePassword(
        @AuthenticationPrincipal Long userId,
        @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(userId, request.currentPassword(), request.newPassword());
        return ApiResponse.success();
    }
}