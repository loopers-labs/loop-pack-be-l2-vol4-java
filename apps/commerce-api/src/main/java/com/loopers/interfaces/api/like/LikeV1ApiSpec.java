package com.loopers.interfaces.api.like;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Like V1 API", description = "Loopers 좋아요 API 입니다.")
public interface LikeV1ApiSpec {

    @Operation(
        summary = "좋아요 등록",
        description = "상품에 좋아요를 등록합니다. 같은 요청이 반복되어도 결과 상태는 동일합니다(멱등).",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true, description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true, description = "비밀번호")
        }
    )
    ApiResponse<Object> like(
        @Parameter(hidden = true) User user,
        @Parameter(name = "productId", in = ParameterIn.PATH, required = true, description = "상품 ID")
        Long productId
    );

    @Operation(
        summary = "좋아요 취소",
        description = "상품의 좋아요를 취소합니다. 같은 요청이 반복되어도 결과 상태는 동일합니다(멱등).",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true, description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true, description = "비밀번호")
        }
    )
    ApiResponse<Object> unlike(
        @Parameter(hidden = true) User user,
        @Parameter(name = "productId", in = ParameterIn.PATH, required = true, description = "상품 ID")
        Long productId
    );
}
