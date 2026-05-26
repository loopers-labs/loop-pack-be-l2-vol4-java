package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.application.like.ProductLikeFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserFacade userFacade;
    private final ProductLikeFacade productLikeFacade;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<UserV1Dto.UserResponse> signup(
        @Valid @RequestBody UserV1Dto.SignupRequest request
    ) {
        UserInfo info = userFacade.signup(
            request.loginId(),
            request.password(),
            request.name(),
            request.birth(),
            request.email()
        );
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(
        @LoginUser AuthenticatedUser user
    ) {
        UserInfo info = userFacade.getMyInfo(user.loginId());
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

    @RequestMapping(value = "/password", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ApiResponse<Void> changePassword(
        @LoginUser AuthenticatedUser user,
        @Valid @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(user.loginId(), request.oldPassword(), request.newPassword());
        return ApiResponse.success(null);
    }

    @GetMapping("/{userId}/likes")
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getLikedProducts(
        @LoginUser AuthenticatedUser user,
        @PathVariable(value = "userId") String userId
    ) {
        if (!user.loginId().equals(userId)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "다른 회원의 좋아요 목록은 조회할 수 없습니다.");
        }

        List<ProductInfo> infos = productLikeFacade.getLikedProducts(user.loginId());
        List<ProductV1Dto.ProductResponse> responses = infos.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
