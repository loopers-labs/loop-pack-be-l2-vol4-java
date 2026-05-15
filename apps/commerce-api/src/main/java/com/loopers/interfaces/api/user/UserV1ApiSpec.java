package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "User V1 API", description = "User 도메인 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "유저 생성",
        description = "CreateUserRequest로 유저를 생성합니다."
    )
    ApiResponse<UserV1Dto.UserResponse> createUser(
        @Schema(name = "유저 생성 DTO", description = "생성할 유저의 기본적인 정보를 사용자에게 받는 DTO")
        @RequestBody @Valid UserV1Dto.CreateUserRequest request
    );

    @Operation(
        summary = "유저 생성",
        description = "CreateUserRequest로 유저를 생성합니다."
    )
    ApiResponse<Object> updateUserPassword(
        @Schema(name = "유저 ID", description = "조회할 유저의 ID")
        @PathVariable Long id,
        @Schema(name = "유저 비밀번호 변경 DTO", description = "현재 비밀번호와 변경할 비밀번호를 가지고 있는 DTO")
        @RequestBody @Valid UserV1Dto.ChangeUserPasswordRequest request
    );

    @Operation(
        summary = "유저 조회",
        description = "ID로 유저를 조회합니다."
    )
    ApiResponse<UserV1Dto.UserResponse> getUserResponse(
        @Schema(name = "유저 ID", description = "조회할 유저의 ID")
        @PathVariable Long id
    );
}
