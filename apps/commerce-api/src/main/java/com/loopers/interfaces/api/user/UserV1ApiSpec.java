package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "Loopers 회원 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원 가입",
        description = "신규 회원을 등록합니다."
    )
    ApiResponse<UserV1Dto.UserResponse> signUp(UserV1Dto.SignUpRequest request);

    @Operation(
        summary = "내 정보 조회",
        description = "X-Loopers-LoginId / X-Loopers-LoginPw 헤더로 인증한 후 내 정보를 반환합니다."
    )
    ApiResponse<UserV1Dto.UserResponse> getMe(String loginId, String password);

    @Operation(
        summary = "비밀번호 변경",
        description = "헤더로 인증한 후, 바디의 currentPassword / newPassword 로 비밀번호를 변경합니다."
    )
    ApiResponse<Void> changePassword(String loginId, String password, UserV1Dto.ChangePasswordRequest request);
}
