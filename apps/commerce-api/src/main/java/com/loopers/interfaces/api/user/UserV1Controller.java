package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.application.user.UserService;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserService userService;

    @PostMapping
    public ApiResponse<UserV1Dto.UserResponse> join(@RequestBody UserV1Dto.UserJoinRequest request) {
        User user = userService.join(request.loginId(), request.loginPassword(), request.name(), request.birthday(), request.email());
        return ApiResponse.success(UserV1Dto.UserResponse.from(UserInfo.from(user)));
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getInfo(@LoginUser User user) {
        return ApiResponse.success(UserV1Dto.UserResponse.from(UserInfo.fromWithMaskedName(user)));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @LoginUser User user,
            @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userService.changePassword(user, request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
