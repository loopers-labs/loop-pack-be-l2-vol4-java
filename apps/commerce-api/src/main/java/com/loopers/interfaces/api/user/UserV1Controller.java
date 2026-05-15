package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserAccountFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserAccountFacade userFacade;

    @PostMapping
    public ApiResponse<UserV1Dto.UserRegisterResponse> register(
        @Valid @RequestBody UserV1Dto.UserRegisterRequest request
    ) {
        UserInfo info = userFacade.register(request.toCommand());
        return ApiResponse.success(UserV1Dto.UserRegisterResponse.from(info));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Object> changePassword(
        @CurrentUser UserModel user,
        @Valid @RequestBody UserV1Dto.UserChangePasswordRequest request
    ) {
        userFacade.changePassword(user, request.toCommand());
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserMeResponse> getMe(@CurrentUser UserModel user) {
        UserInfo info = userFacade.getMe(user);
        return ApiResponse.success(UserV1Dto.UserMeResponse.from(info));
    }
}
