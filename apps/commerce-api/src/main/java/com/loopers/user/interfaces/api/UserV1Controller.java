package com.loopers.user.interfaces.api;

import com.loopers.user.application.UserResult;
import com.loopers.user.application.UserService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserService userService;

    @PostMapping
    @Override
    public ApiResponse<UserV1Response.Detail> signUp(
        @Valid @RequestBody UserV1Request.SignUp request
    ) {
        UserResult.Detail result = userService.signUp(request.toCommand());
        return ApiResponse.success(UserV1Response.Detail.from(result));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Response.Masked> getMyInfo(@AuthenticationPrincipal Long userId) {
        UserResult.Detail result = userService.get(userId);
        return ApiResponse.success(UserV1Response.Masked.from(result));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Void> changePassword(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody UserV1Request.UpdatePassword request
    ) {
        userService.changePassword(request.toCommand(userId));
        return ApiResponse.success(null);
    }
}
