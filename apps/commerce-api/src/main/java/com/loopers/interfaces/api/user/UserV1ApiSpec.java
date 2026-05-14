package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "회원 도메인 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(summary = "회원 가입", description = "회원 정보를 등록합니다.")
    ApiResponse<UserV1Dto.SignUpResponse> signUp(UserV1Dto.SignUpRequest request);
}
