package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserFacade userFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserV1Dto.SignUpResponse> signUp(@RequestBody UserV1Dto.SignUpRequest request) {
        UserModel user = new UserModel(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        UserInfo info = userFacade.signUp(user);
        return ApiResponse.success(UserV1Dto.SignUpResponse.from(info));
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getUser(
        @RequestAttribute("loginId") String loginId,
        @RequestAttribute("password") String password
    ) {
        UserInfo info = userFacade.getUser(loginId, password);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> updatePassword(
        @RequestAttribute("loginId") String loginId,
        @RequestAttribute("password") String password,
        @RequestBody UserV1Dto.UpdatePasswordRequest request
    ) {
        userFacade.updatePassword(loginId, request.oldPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
