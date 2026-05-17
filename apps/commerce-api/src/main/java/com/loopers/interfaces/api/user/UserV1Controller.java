package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.interfaces.api.user.dto.ChangePasswordV1Request;
import com.loopers.interfaces.api.user.dto.MyInfoV1Response;
import com.loopers.interfaces.api.user.dto.SignUpV1Request;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping
    @Override
    public ApiResponse<Void> signUp(@Valid @RequestBody SignUpV1Request request) {
        userFacade.signUp(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<MyInfoV1Response> getMyInfo(@LoginUser AuthUser authUser) {
        UserInfo info = userFacade.getMyInfo(authUser.id());
        return ApiResponse.success(MyInfoV1Response.from(info));
    }

    @PatchMapping("/me/password")
    @Override
    public ApiResponse<Void> changePassword(@LoginUser AuthUser authUser, @Valid @RequestBody ChangePasswordV1Request request) {
        userFacade.changePassword(authUser.id(), request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
