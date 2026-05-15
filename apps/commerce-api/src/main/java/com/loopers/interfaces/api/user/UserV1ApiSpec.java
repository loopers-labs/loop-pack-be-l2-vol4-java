package com.loopers.interfaces.api.user;


import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "User V1 API", description = "회원 관련 API")
public interface UserV1ApiSpec {
    @Operation(summary = "회원 가입")
    ApiResponse<UserV1Dto.UserResponse> signUp(UserV1Dto.SignUpRequest request);

    @Operation(summary = "내 정보 조회", description = "헤더 인증된 본인 정보를 반환합니다.")
    ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(String loginId, String loginPw);
}
