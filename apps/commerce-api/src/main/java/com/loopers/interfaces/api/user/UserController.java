package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserFacade userFacade;

    @GetMapping("/me")
    public ApiResponse<UserDto.UserResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        UserInfo info = userFacade.getUser(principal.getId());
        return ApiResponse.success(UserDto.UserResponse.from(info));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody UserDto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(principal.getId(), request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
