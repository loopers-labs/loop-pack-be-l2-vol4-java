package com.loopers.interfaces.api.user;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.UserService;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserService userService;
    private final LikeFacade likeFacade;

    @PostMapping
    @Override
    public ApiResponse<UserV1Dto.UserResponse> signUp(@Valid @RequestBody UserV1Dto.SignUpRequest request) {
        UserModel user = userService.create(
                request.loginId(),
                request.password(),
                request.name(),
                request.birthDate(),
                request.email(),
                request.gender()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.from(UserInfo.from(user)));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(
        @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
        @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw
    ) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        return ApiResponse.success(UserV1Dto.UserResponse.fromMasked(UserInfo.from(user)));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Void> changePassword(
        @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
        @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
        @Valid @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userService.changePassword(loginId, loginPw, request.oldPassword(), request.newPassword());
        return ApiResponse.success(null);
    }

    @GetMapping("/{userId}/likes")
    @Override
    public ApiResponse<List<UserV1Dto.LikedProductResponse>> getLikedProducts(
        @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
        @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
        @PathVariable Long userId
    ) {
        List<ProductInfo> products = likeFacade.getLikedProducts(loginId, loginPw, userId);
        return ApiResponse.success(products.stream()
                .map(UserV1Dto.LikedProductResponse::from)
                .toList());
    }
}
