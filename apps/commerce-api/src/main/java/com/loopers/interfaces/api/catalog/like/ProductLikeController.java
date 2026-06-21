package com.loopers.interfaces.api.catalog.like;

import com.loopers.application.catalog.like.ProductLikeCommand;
import com.loopers.application.catalog.like.ProductLikeCommandService;
import com.loopers.application.catalog.like.ProductLikeQuery;
import com.loopers.application.catalog.like.ProductLikeQueryService;
import com.loopers.application.catalog.like.ProductLikeResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping
public class ProductLikeController {

    private final ProductLikeCommandService productLikeCommandService;
    private final ProductLikeQueryService productLikeQueryService;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<ProductLikeDto.ProductLikeResponse> like(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw,
        @PathVariable Long productId
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        ProductLikeResult result = productLikeCommandService.like(new ProductLikeCommand.Like(loginId, productId));
        return ApiResponse.success(ProductLikeDto.ProductLikeResponse.from(result));
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<ProductLikeDto.ProductLikeResponse> unlike(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw,
        @PathVariable Long productId
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        ProductLikeResult result = productLikeCommandService.unlike(new ProductLikeCommand.Unlike(loginId, productId));
        return ApiResponse.success(ProductLikeDto.ProductLikeResponse.from(result));
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<PageResponse<ProductLikeDto.ProductLikeResponse>> getMyLikes(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw,
        @PathVariable String userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        if (!userId.equals(loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }

        PageResult<ProductLikeResult> result = productLikeQueryService.getMyLikes(
            new ProductLikeQuery.MyLikes(userId, page, size)
        );
        return ApiResponse.success(PageResponse.from(result, ProductLikeDto.ProductLikeResponse::from));
    }
}
