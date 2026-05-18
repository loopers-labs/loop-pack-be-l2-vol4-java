package com.loopers.interfaces.api.user;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "Loopers 사용자 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "회원가입을 진행합니다."
    )
    ApiResponse<UserV1Dto.UserResponse> signup(
        @Schema(description = "회원가입 요청 정보")
        UserV1Dto.SignupRequest request
    );

    @Operation(
        summary = "내 정보 조회",
        description = "인증 헤더로 본인의 사용자 정보를 조회합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true,
                description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true,
                description = "비밀번호")
        }
    )
    ApiResponse<UserV1Dto.MyInfoResponse> me(
        @Parameter(hidden = true) User user
    );

    @Operation(
        summary = "비밀번호 변경",
        description = "인증 헤더로 본인의 비밀번호를 변경합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true,
                description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true,
                description = "현재 비밀번호")
        }
    )
    ApiResponse<Object> changePassword(
        @Parameter(hidden = true) User user,
        @Schema(description = "비밀번호 변경 요청") UserV1Dto.ChangePasswordRequest request
    );
}
