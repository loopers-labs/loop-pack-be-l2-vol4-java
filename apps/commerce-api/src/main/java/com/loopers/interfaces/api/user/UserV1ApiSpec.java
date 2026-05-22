package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "User V1 API", description = "Loopers 유저 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "로그인 ID, 비밀번호, 이름, 생년월일, 이메일로 신규 회원을 등록합니다."
    )
    ApiResponse<UserV1Dto.UserResponse> signUp(@Valid UserV1Dto.SignUpRequest request);
}
