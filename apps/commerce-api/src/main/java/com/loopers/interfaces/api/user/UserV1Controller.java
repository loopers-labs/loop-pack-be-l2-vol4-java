package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.application.like.ProductLikeFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductDto;
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
    private final UserService userService;
    private final ProductLikeFacade productLikeFacade;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<UserDto.Register.V1.Response> signup(
        @Valid @RequestBody UserDto.Register.V1.Request request
    ) {
        UserInfo info = userFacade.signup(
            request.loginId(),
            request.password(),
            request.name(),
            request.birth(),
            request.email()
        );
        UserDto.Register.V1.Response response = UserDto.Register.V1.Response.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<UserDto.GetMyInfo.V1.Response> getMyInfo(
        @LoginUser AuthenticatedUser user
    ) {
        UserInfo info = userFacade.getMyInfo(user.loginId());
        UserDto.GetMyInfo.V1.Response response = UserDto.GetMyInfo.V1.Response.from(info);
        return ApiResponse.success(response);
    }

    @RequestMapping(value = "/password", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ApiResponse<Void> changePassword(
        @LoginUser AuthenticatedUser user,
        @Valid @RequestBody UserDto.ChangePassword.V1.Request request
    ) {
        userService.changePassword(user.loginId(), request.oldPassword(), request.newPassword());
        return ApiResponse.success(null);
    }

    @GetMapping("/{userId}/likes")
    public ApiResponse<List<ProductDto.List.V1.Response>> getLikedProducts(
        @LoginUser AuthenticatedUser user,
        @PathVariable(value = "userId") String userId
    ) {
        if (!user.loginId().equals(userId)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "다른 회원의 좋아요 목록은 조회할 수 없습니다.");
        }

        List<ProductInfo> infos = productLikeFacade.getLikedProducts(user.loginId());
        List<ProductDto.List.V1.Response> responses = infos.stream()
            .map(ProductDto.List.V1.Response::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
