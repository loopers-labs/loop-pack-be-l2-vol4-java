package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserFacade userFacade;

    @PostMapping
    public ApiResponse<UserV1Dto.UserResponse> join(@RequestBody UserV1Dto.UserJoinRequest request) {
        UserInfo userInfo = userFacade.join(request.toCommand());
        return ApiResponse.success(UserV1Dto.UserResponse.from(userInfo));
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getInfo(@LoginUser User user) {
        UserInfo userInfo = userFacade.getUser(user);
        return ApiResponse.success(UserV1Dto.UserResponse.from(userInfo));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @LoginUser User user,
            @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(user, new UserCommand.ChangePassword(request.currentPassword(), request.newPassword()));
        return ApiResponse.success(null);
    }
}
