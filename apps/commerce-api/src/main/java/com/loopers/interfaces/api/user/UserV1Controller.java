package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
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
    public ApiResponse<UserV1Dto.UserResponse> getInfo(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPassword
    ) {
        UserInfo userInfo = userFacade.getUser(new UserCommand.GetUser(loginId, loginPassword));
        return ApiResponse.success(UserV1Dto.UserResponse.from(userInfo));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPassword,
            @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(new UserCommand.ChangePassword(loginId, loginPassword, request.currentPassword(), request.newPassword()));
        return ApiResponse.success(null);
    }
}
