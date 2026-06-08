package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/users")
public class UserController {

    private final UserFacade userFacade;

    @PostMapping("/signup")
    public ApiResponse<Void> signUp(@RequestBody UserV1Dto.SignUpRequest request) {
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
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        UserInfo info = userFacade.getMyInfo(loginId, password);
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> updatePassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @RequestBody UserV1Dto.UpdatePasswordRequest request
    ) {
        userFacade.updatePassword(
                loginId,
                password,
                request.oldPassword(),
                request.newPassword()
        );
        return ApiResponse.success(null);
    }
}
