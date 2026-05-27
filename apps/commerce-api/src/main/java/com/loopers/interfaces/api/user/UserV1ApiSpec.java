package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@Tag(name = "User V1 API", description = "Loopers 회원 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(summary = "회원가입", description = "신규 회원을 등록합니다.")
    ApiResponse<UserV1Dto.UserResponse> signUp(UserV1Dto.SignUpRequest request);

    @Operation(summary = "내 정보 조회", description = "로그인 ID 로 내 정보를 조회합니다.")
    ApiResponse<UserV1Dto.UserResponse> getMyInfo(@RequestHeader String loginId, @RequestHeader String loginPw);

    @Operation(summary = "비밀번호 수정", description = "기존 비밀번호 확인 후 신규 비밀번호로 변경합니다.")
    ApiResponse<Void> changePassword(@RequestHeader String loginId, @RequestHeader String loginPw, UserV1Dto.ChangePasswordRequest request);

    @Operation(summary = "좋아요 상품 목록 조회", description = "내가 좋아요 한 상품 목록을 조회합니다.")
    ApiResponse<List<UserV1Dto.LikedProductResponse>> getLikedProducts(@RequestHeader String loginId, @RequestHeader String loginPw, Long userId);
}
