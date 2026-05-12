package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserFacade userFacade;

    @PostMapping
    public ApiResponse<UserV1Dto.UserResponse> createUser(
            @Valid @RequestBody UserV1Dto.CreateUserRequest request
    ) {
        UserInfo userInfo = userFacade.createUser(
                request.loginId(),
                request.password(),
                request.name(),
                request.birthDate(),
                request.email()
        );
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(userInfo);
        return ApiResponse.success(response);
    }
}
