package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "회원 API")
public interface UserV1ApiSpec {

    @Operation(summary = "회원 가입", description = "신규 회원을 등록합니다.")
    ApiResponse<UserV1Dto.UserResponse> register(UserV1Dto.RegisterRequest request);

    @Operation(summary = "내 정보 조회", description = "아이디로 회원 정보를 조회합니다.")
    ApiResponse<UserV1Dto.UserResponse> getUser(String userid);

    @Operation(summary = "비밀번호 변경", description = "회원의 비밀번호를 변경합니다.")
    ApiResponse<UserV1Dto.UserResponse> changePassword(String loginId, UserV1Dto.ChangePasswordRequest request);
}
