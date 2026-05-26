package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserFacade userFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDto.SignUpResponse> signUp(@Valid @RequestBody UserDto.SignUpRequest request) {
        UserInfo info = userFacade.signUp(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        return ApiResponse.success(UserDto.SignUpResponse.from(info));
    }

    @GetMapping("/me")
    public ApiResponse<UserDto.UserResponse> getUser(
        @RequestAttribute("userId") Long userId
    ) {
        UserInfo info = userFacade.getUser(userId);
        return ApiResponse.success(UserDto.UserResponse.from(info));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> updatePassword(
        @RequestAttribute("userId") Long userId,
        @Valid @RequestBody UserDto.UpdatePasswordRequest request
    ) {
        userFacade.updatePassword(userId, request.oldPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
