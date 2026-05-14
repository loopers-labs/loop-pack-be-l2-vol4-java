package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping
    @Override
    public ApiResponse<UserV1Dto.RegisterResponse> register(@RequestBody @Valid UserV1Dto.RegisterRequest request) {
        UserInfo info = userFacade.register(
            request.loginId(), request.password(), request.name(), request.birth(), request.email()
        );
        return ApiResponse.success(UserV1Dto.RegisterResponse.from(info));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(
        @RequestHeader("X-Loopers-LoginId")
        @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "로그인 ID는 영문/숫자 1~10자여야 합니다.") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        UserInfo info = userFacade.getMyInfo(loginId, password);
        return ApiResponse.success(UserV1Dto.MyInfoResponse.from(info));
    }

    @PutMapping("/me/password")
    @Override
    public ApiResponse<Void> changePassword(
        @RequestHeader("X-Loopers-LoginId")
        @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "로그인 ID는 영문/숫자 1~10자여야 합니다.") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(loginId, password, request.newPassword());
        return ApiResponse.success(null);
    }
}
