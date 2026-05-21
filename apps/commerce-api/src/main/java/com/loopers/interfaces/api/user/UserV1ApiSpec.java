package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User API", description = "사용자 API")
public interface UserV1ApiSpec {

    @Operation(summary = "회원가입", description = "로그인 ID, 비밀번호, 이름, 생년월일, 이메일로 회원가입합니다.")
    ApiResponse<UserV1Dto.UserResponse> signUp(UserV1Dto.SignUpRequest request);

    @Operation(summary = "내 정보 조회", description = "로그인한 회원의 본인 정보를 조회합니다.")
    ApiResponse<UserV1Dto.MyInfoResponse> getMe(LoginUser loginUser);

    @Operation(summary = "비밀번호 수정", description = "로그인한 회원의 비밀번호를 수정합니다.")
    ApiResponse<Object> changePassword(LoginUser loginUser, UserV1Dto.ChangePasswordRequest request);
}
