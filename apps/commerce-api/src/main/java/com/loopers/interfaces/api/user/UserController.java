package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginCredentials;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.interfaces.api.user.dto.ChangePasswordRequest;
import com.loopers.interfaces.api.user.dto.SignUpRequest;
import com.loopers.interfaces.api.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserFacade userFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@RequestBody SignUpRequest request) {
        UserInfo userInfo = userFacade.signUp(request.toCommand());
        UserResponse response = UserResponse.from(userInfo);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(@LoginUser LoginCredentials credentials) {
        userFacade.authenticate(credentials.loginId(), credentials.loginPw()); // 해당 메소드는 요구사항과는 관련 없음
        UserInfo userInfo = userFacade.getMyInfo(credentials.loginId());
        return ResponseEntity.ok(ApiResponse.success(UserResponse.maskedFrom(userInfo)));
    }

    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<UserResponse>> changePassword(
        @LoginUser LoginCredentials credentials,
        @RequestBody ChangePasswordRequest request
    ) {
        userFacade.changePassword(credentials.loginId(), request.toCommand(credentials.loginPw()));
        return ResponseEntity.ok(ApiResponse.success(UserResponse.empty()));
    }
}
