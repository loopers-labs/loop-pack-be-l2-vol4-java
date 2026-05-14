package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "Loopers 사용자 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "신규 사용자를 등록합니다."
    )
    ApiResponse<UserV1Dto.SignUpResponse> signUp(UserV1Dto.SignUpRequest request);

    @Operation(
        summary = "내 정보 조회",
        description = "X-Loopers-LoginId / X-Loopers-LoginPw 헤더로 인증된 사용자의 정보를 반환합니다. 이름은 마지막 글자가 *로 마스킹됩니다."
    )
    ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(@LoginUser UserInfo loginUser);

    @Operation(
        summary = "비밀번호 수정",
        description = "로그인된 사용자의 비밀번호를 변경합니다. 기존 비밀번호와 동일하거나 생년월일을 포함할 수 없습니다."
    )
    ApiResponse<Object> updatePassword(@LoginUser UserInfo loginUser, UserV1Dto.UpdatePasswordRequest request);
}
