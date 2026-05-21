package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping
    @Override
    public ApiResponse<UserV1Dto.UserResponse> signUp(
            @RequestBody UserV1Dto.SignUpRequest request
    ) {
        UserInfo info = userFacade.signUp(
                request.loginId(), request.password(), request.name(), request.birthDate(), request.email()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(
            @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
            @RequestHeader(value = "X-Loopers-LoginPw", required = false) String loginPw
    ) {
        if (loginId == null || loginPw == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-Loopers 헤더가 누락되었습니다.");
        }
        UserInfo info = userFacade.getMyInfo(loginId, loginPw);
        return ApiResponse.success(UserV1Dto.MyInfoResponse.from(info));
    }

    @PatchMapping("/me/password")
    @Override
    public ApiResponse<Object> changePassword(
            @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
            @RequestHeader(value = "X-Loopers-LoginPw", required = false) String loginPw,
            @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        if (loginId == null || loginPw == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-Loopers 헤더가 누락되었습니다.");
        }
        // 헤더 인증 (현재 비번이 헤더와 일치하는지 확인)
        if (!loginPw.equals(request.curPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        userFacade.changePassword(loginId, request.curPassword(), request.newPassword());
        return ApiResponse.success();
    }

}
