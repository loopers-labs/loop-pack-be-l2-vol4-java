package com.loopers.interfaces.api.user;

import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "Loopers 회원 도메인 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원 가입",
        description = "신규 회원을 등록한다."
    )
    ApiResponse<UserV1Dto.SignUpResponse> signUp(UserV1Dto.SignUpRequest request);

    @Operation(
        summary = "내 정보 조회",
        description = "본인 인증된 회원의 정보를 반환한다. 이름은 마지막 1글자가 *로 마스킹되어 반환된다."
    )
    ApiResponse<UserV1Dto.MyInfoResponse> readMyInfo(
        @Parameter(hidden = true) @AuthenticatedUser UserModel authenticatedUser
    );
}
