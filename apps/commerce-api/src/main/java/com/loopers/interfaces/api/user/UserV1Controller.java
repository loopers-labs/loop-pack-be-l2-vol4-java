package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserService;
import com.loopers.domain.user.User;
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
    public ApiResponse<UserV1Dto.UserResponse> signUp(
        @Valid @RequestBody UserV1Dto.SignUpRequest request
    ) {
        User user = userService.signUp(request.toCommand());
        return ApiResponse.success(UserV1Dto.UserResponse.from(user));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.UserInfoResponse> getMyInfo(@AuthenticationPrincipal Long userId) {
        User user = userService.get(userId);
        return ApiResponse.success(UserV1Dto.UserInfoResponse.from(user));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Void> changePassword(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody UserV1Dto.UpdatePasswordRequest request
    ) {
        userService.changePassword(request.toCommand(userId));
        return ApiResponse.success(null);
    }
}
