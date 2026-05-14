package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping
    @Override
    public ApiResponse<UserV1Dto.UserResponse> signUp(@RequestBody UserV1Dto.SignUpRequest request) {
        UserInfo.User user = userFacade.signUp(request.toCommand());
        return ApiResponse.success(UserV1Dto.UserResponse.from(user));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.MyInfoResponse> getMe(@AuthenticatedUser LoginUser loginUser) {
        UserInfo.User user = userFacade.getMe(loginUser.id());
        return ApiResponse.success(UserV1Dto.MyInfoResponse.from(user));
    }

    @PutMapping("/me/password")
    @Override
    public ApiResponse<Object> changePassword(
        @AuthenticatedUser LoginUser loginUser,
        @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(loginUser.id(), request.toCommand());
        return ApiResponse.success();
    }
}
