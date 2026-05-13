package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;

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
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser
    );

    @Operation(
        summary = "비밀번호 수정",
        description = "본인 인증된 회원의 비밀번호를 새 값으로 교체한다. 본문 currentPassword로 변경 의도를 재확인하고, newPassword가 RULE(8~16자, 영문/숫자/특수문자, 생년월일 포함 불가)을 만족하면 BCrypt 해시로 갱신한다."
    )
    ApiResponse<Void> changePassword(
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser,
        UserV1Dto.ChangePasswordRequest request
    );
}
