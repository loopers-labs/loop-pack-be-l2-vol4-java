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
public class UserV1Controller {

    private final UserFacade userFacade;
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PASSWORD_HEADER = "X-Loopers-LoginPw";

    @PostMapping
    public ApiResponse<UserV1Dto.UserResponse> registerUser(
            @RequestBody UserV1Dto.RegisterUserRequest request
    ) {
        UserInfo info = userFacade.registerUser(
                request.userId(),
                request.password(),
                request.name(),
                request.birthDate(),
                request.email()
        );
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getUser(
            @RequestHeader(LOGIN_ID_HEADER) String userId,
            @RequestHeader(LOGIN_PASSWORD_HEADER) String password
    ) {
        UserInfo info = userFacade.getUser(userId, password);
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

    @PatchMapping("/me/password")
    public ApiResponse<UserV1Dto.UserResponse> changePassword(
            @RequestHeader(LOGIN_ID_HEADER) String userId,
            @RequestHeader(LOGIN_PASSWORD_HEADER) String currentPassword,
            @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        UserInfo info = userFacade.changePassword(
                userId,
                currentPassword,
                request.newPassword()
        );
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

}
