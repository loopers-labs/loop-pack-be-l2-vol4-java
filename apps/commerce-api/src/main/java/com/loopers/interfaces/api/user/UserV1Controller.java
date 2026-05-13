package com.loopers.interfaces.api.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserMyInfo;
import com.loopers.application.user.UserSignUpInfo;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserV1Dto.SignUpResponse> signUp(@RequestBody UserV1Dto.SignUpRequest request) {
        UserSignUpInfo newUserSignUpInfo = userFacade.signUp(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );

        return ApiResponse.success(UserV1Dto.SignUpResponse.from(newUserSignUpInfo));
    }

    @Override
    @GetMapping("/me")
    public ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(@AuthenticatedUser UserModel authenticatedUser) {
        UserMyInfo userMyInfo = userFacade.getMyInfo(authenticatedUser);

        return ApiResponse.success(UserV1Dto.MyInfoResponse.from(userMyInfo));
    }
}
