package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "Loopers 유저 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "로그인 ID, 비밀번호, 이름, 생년월일, 이메일로 회원가입합니다."
    )
    ApiResponse<Object> signup(UserV1Dto.SignupRequest request);

    @Operation(
        summary = "내 정보 조회",
        description = "헤더의 로그인 ID와 비밀번호로 인증 후 내 정보를 반환합니다. 이름은 마지막 글자가 마스킹됩니다."
    )
    ApiResponse<UserV1Dto.UserResponse> getMyInfo(
        @Parameter(description = "로그인 ID") String userId,
        @Parameter(description = "비밀번호") String password
    );

    @Operation(
        summary = "비밀번호 수정",
        description = "기존 비밀번호와 새 비밀번호를 입력해 비밀번호를 변경합니다."
    )
    ApiResponse<Object> changePassword(
        @Parameter(description = "로그인 ID") String userId,
        @Parameter(description = "비밀번호") String password,
        UserV1Dto.ChangePasswordRequest request
    );
}
