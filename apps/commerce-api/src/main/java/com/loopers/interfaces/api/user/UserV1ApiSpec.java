package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "Loopers 회원 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(summary = "회원가입", description = "새 회원을 등록합니다.")
    ApiResponse<UserV1Dto.UserResponse> register(UserV1Dto.RegisterRequest request);

    @Operation(summary = "내 정보 조회", description = "인증된 회원의 정보를 조회합니다.")
    ApiResponse<UserV1Dto.UserResponse> getMe(AuthUserContext authUser);

    @Operation(summary = "비밀번호 수정", description = "인증된 회원의 비밀번호를 수정합니다.")
    ApiResponse<Void> changePassword(AuthUserContext authUser, UserV1Dto.ChangePasswordRequest request);
}
