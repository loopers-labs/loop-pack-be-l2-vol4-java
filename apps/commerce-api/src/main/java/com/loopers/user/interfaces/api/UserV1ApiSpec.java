package com.loopers.user.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "User V1 API", description = "Loopers 유저 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "로그인 ID, 비밀번호, 이름, 생년월일, 이메일로 신규 회원을 등록합니다."
    )
    ApiResponse<UserV1Response.Detail> signUp(@Valid UserV1Request.SignUp request);

    @Operation(
        summary = "내 정보 조회",
        description = "X-Loopers-LoginId / X-Loopers-LoginPw 헤더로 인증된 사용자의 정보를 조회합니다."
    )
    ApiResponse<UserV1Response.Masked> getMyInfo(@Parameter(hidden = true) Long userId);

    @Operation(
        summary = "비밀번호 수정",
        description = "X-Loopers-LoginId / X-Loopers-LoginPw 헤더로 인증한 사용자의 비밀번호를 변경합니다."
    )
    ApiResponse<Void> changePassword(
        @Parameter(hidden = true) Long userId,
        @Valid UserV1Request.UpdatePassword request
    );
}
