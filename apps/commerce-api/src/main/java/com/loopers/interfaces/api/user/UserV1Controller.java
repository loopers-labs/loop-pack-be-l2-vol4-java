package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @RequestHeader(value = "X-Loopers-LoginPw", required = false) String password
    ) {
        if (loginId == null || password == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다.");
        }
        UserInfo info = userFacade.getUser(loginId, password);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> updatePassword(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
        @RequestBody UserV1Dto.UpdatePasswordRequest request
    ) {
        if (loginId == null || password == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다.");
        }
        userFacade.updatePassword(loginId, request.oldPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
