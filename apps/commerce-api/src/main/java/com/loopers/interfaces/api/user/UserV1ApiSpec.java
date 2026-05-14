package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@Tag(name = "User V1 API", description = "Loopers 회원 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(summary = "회원 가입", description = "신규 회원을 등록합니다.")
    ApiResponse<UserV1Dto.RegisterResponse> register(@Valid UserV1Dto.RegisterRequest request);

    @Operation(summary = "내 정보 조회", description = "인증된 회원의 정보를 조회합니다. 이름은 마스킹되어 반환됩니다.")
    ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(
        @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "로그인 ID는 영문/숫자 1~10자여야 합니다.") String loginId,
        String password
    );

    @Operation(summary = "비밀번호 변경", description = "인증된 회원의 비밀번호를 변경합니다.")
    ApiResponse<Void> changePassword(
        @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "로그인 ID는 영문/숫자 1~10자여야 합니다.") String loginId,
        String password,
        UserV1Dto.ChangePasswordRequest request
    );
}
