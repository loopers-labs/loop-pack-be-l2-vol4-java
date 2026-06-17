package com.loopers.interfaces.api.productlike;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "ProductLike", description = "상품 좋아요 API")
public interface ProductLikeV1ApiSpec {

    @Operation(summary = "상품 좋아요 등록", description = "상품에 좋아요를 등록한다. 이미 좋아요 상태면 무시하고 성공 처리한다(멱등).")
    ApiResponse<Object> like(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @Parameter(description = "상품 ID") @PathVariable("productId") Long productId
    );

    @Operation(summary = "상품 좋아요 취소", description = "상품 좋아요를 취소한다. 좋아요 상태가 아니어도 무시하고 성공 처리한다(멱등).")
    ApiResponse<Object> unlike(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @Parameter(description = "상품 ID") @PathVariable("productId") Long productId
    );

    @Operation(summary = "내가 좋아요한 상품 목록 조회", description = "로그인 사용자가 좋아요한 상품 목록을 반환한다. 본인만 조회 가능하다.")
    ApiResponse<ProductLikeV1Dto.LikedProductsResponse> getLikedProducts(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @Parameter(description = "사용자 식별자(loginId)") @PathVariable("userId") String userId
    );
}
