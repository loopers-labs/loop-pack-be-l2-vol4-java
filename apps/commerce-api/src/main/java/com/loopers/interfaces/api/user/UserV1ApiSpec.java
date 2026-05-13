package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.CurrentUser;
import com.loopers.interfaces.api.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "회원 API")
public interface UserV1ApiSpec {

    @Operation(summary = "회원 가입", description = "신규 회원을 등록합니다.")
    ApiResponse<UserV1Dto.UserResponse> signUp(UserV1Dto.SignUpRequest request);

    @Operation(summary = "내 정보 조회", description = "로그인한 회원의 정보를 조회합니다.")
    ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(@CurrentUser LoginUser loginUser);

    @Operation(summary = "비밀번호 변경", description = "로그인한 회원의 비밀번호를 변경합니다.")
    ApiResponse<Void> changePassword(@CurrentUser LoginUser loginUser, UserV1Dto.ChangePasswordRequest request);
}
