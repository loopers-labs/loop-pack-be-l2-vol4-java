package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.LoginUser;
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
public class UserController {

    private final UserFacade userFacade;

    @PostMapping
    public ApiResponse<Void> signUp(@RequestBody UserDto.SignUpRequest request) {
        userFacade.signUp(request.toCommand());
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserDto.MeResponse> getMyInfo(@LoginUser AuthUser authUser) {
        UserInfo info = userFacade.getMyInfo(authUser.id());
        return ApiResponse.success(UserDto.MeResponse.from(info));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
        @LoginUser AuthUser authUser,
        @RequestBody UserDto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(authUser.id(), request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
