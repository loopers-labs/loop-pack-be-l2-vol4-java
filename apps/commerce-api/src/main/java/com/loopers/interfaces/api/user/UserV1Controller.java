package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserFacade userFacade;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<UserV1Dto.UserResponse> signup(
        @Valid @RequestBody UserV1Dto.SignupRequest request
    ) {
        UserInfo info = userFacade.signup(
            request.loginId(),
            request.password(),
            request.name(),
            request.birth(),
            request.email()
        );
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(
        @LoginUser AuthenticatedUser user
    ) {
        UserInfo info = userFacade.getMyInfo(user.loginId());
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(
        @LoginUser AuthenticatedUser user,
        @Valid @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(user.loginId(), request.oldPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
