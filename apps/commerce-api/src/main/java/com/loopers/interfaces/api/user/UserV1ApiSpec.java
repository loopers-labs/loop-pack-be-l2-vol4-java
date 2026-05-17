package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.user.dto.ChangePasswordV1Request;
import com.loopers.interfaces.api.user.dto.MyInfoV1Response;
import com.loopers.interfaces.api.user.dto.SignUpV1Request;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "Loopers 사용자 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "신규 사용자를 등록합니다."
    )
    ApiResponse<Void> signUp(SignUpV1Request request);

    @Operation(
        summary = "내 정보 조회",
        description = "인증된 사용자의 정보를 조회합니다. 이름은 마스킹되어 반환됩니다."
    )
    ApiResponse<MyInfoV1Response> getMyInfo(AuthUser authUser);

    @Operation(
        summary = "비밀번호 변경",
        description = "인증된 사용자의 비밀번호를 변경합니다."
    )
    ApiResponse<Void> changePassword(AuthUser authUser, ChangePasswordV1Request request);
}
