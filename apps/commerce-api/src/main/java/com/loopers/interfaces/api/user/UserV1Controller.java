package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping
    @Override
    public ApiResponse<UserV1Dto.UserResponse> createUser(
        @RequestBody @Valid UserV1Dto.CreateUserRequest request
    ) {
         UserInfo info = userFacade.createUser(
            request.loginId(),
            request.name(),
            request.birth(),
            request.password(),
            request.email()
        );

        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);

        return ApiResponse.success(response);
    }

    @PatchMapping("/{id}")
    @Override
    public ApiResponse<Object> updateUserPassword(
        @PathVariable Long id,
        @RequestBody @Valid UserV1Dto.ChangeUserPasswordRequest request
    ) {
        userFacade.changePassword(id, request.oldPassword(), request.targetPassword());

        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> getUserResponse(@PathVariable Long id) {
        UserInfo userInfo = userFacade.getUserInfo(id);

        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(userInfo);

        return ApiResponse.success(response);
    }
}
