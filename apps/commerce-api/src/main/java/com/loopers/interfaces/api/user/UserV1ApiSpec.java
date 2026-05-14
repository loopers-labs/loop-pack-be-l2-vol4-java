package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "회원 도메인 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(summary = "회원 가입", description = "회원 정보를 등록합니다.")
    ApiResponse<UserV1Dto.SignUpResponse> signUp(UserV1Dto.SignUpRequest request);

    @Operation(summary = "내 정보 조회", description = "헤더 인증 정보로 자신의 회원 정보를 조회합니다.")
    ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(String loginId, String loginPw);
}
