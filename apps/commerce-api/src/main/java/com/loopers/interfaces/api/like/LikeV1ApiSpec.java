package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Like V1 API", description = "Loopers 좋아요 API 입니다.")
public interface LikeV1ApiSpec {

    @Operation(summary = "상품 좋아요 등록", description = "상품에 좋아요를 등록합니다.")
    ApiResponse<Void> like(@RequestHeader String loginId, @RequestHeader String loginPw, Long productId);

    @Operation(summary = "상품 좋아요 취소", description = "상품에 등록한 좋아요를 취소합니다.")
    ApiResponse<Void> unlike(@RequestHeader String loginId, @RequestHeader String loginPw, Long productId);
}
