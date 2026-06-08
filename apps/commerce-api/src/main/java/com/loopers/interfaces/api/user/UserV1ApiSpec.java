package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "User", description = "유저 API")
public interface UserV1ApiSpec {

    @Operation(summary = "유저 생성", description = "loginId/loginPw로 유저를 생성한다.")
    ApiResponse<UserV1Dto.UserResponse> createUser(@RequestBody UserV1Dto.CreateUserRequest request);
}
