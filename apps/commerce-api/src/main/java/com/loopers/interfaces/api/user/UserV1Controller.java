package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.interceptor.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserFacade userFacade;

    @PostMapping
    public ApiResponse<Object> register(@RequestBody UserV1Dto.UserRegisterRequest body) {
        userFacade.register(body.toCommand());
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getMe(HttpServletRequest request) {
        UserModel authenticatedUser = (UserModel) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        UserInfo info = userFacade.getMe(authenticatedUser);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Object> changePassword(HttpServletRequest request, @RequestBody UserV1Dto.PasswordChangeRequest body) {
        UserModel authenticatedUser = (UserModel) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        userFacade.changePassword(authenticatedUser, body.currentPassword(), body.newPassword());
        return ApiResponse.success();
    }
}
