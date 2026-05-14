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

    @PostMapping
    public ApiResponse<Object> signup(
        @RequestBody UserV1Dto.SignupRequest request
    ) {
        userFacade.signup(
            request.userId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        return ApiResponse.success();
    }

    @GetMapping("/myInfo")
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(
        @RequestHeader("X-Loopers-LoginId") String userId,
        @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        UserInfo user = userFacade.getUser(userId, password);
        return ApiResponse.success(UserV1Dto.UserResponse.from(user));
    }

    @PatchMapping("/myInfo/changePassword")
    public ApiResponse<Object> changePassword(
        @RequestHeader("X-Loopers-LoginId") String userId,
        @RequestHeader("X-Loopers-LoginPw") String password, // 헤더 필수 검증용, 비즈니스 로직 미사용
        @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(
            userId,
            request.currentPassword(),
            request.newPassword()
        );
        return ApiResponse.success();
    }
}
