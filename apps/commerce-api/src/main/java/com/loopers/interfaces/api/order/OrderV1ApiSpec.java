package com.loopers.interfaces.api.order;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order V1 API", description = "Loopers 주문 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(
        summary = "주문 접수",
        description = "여러 상품을 한 번에 주문합니다. 재고가 차감되고, 주문 시점의 상품 정보가 스냅샷으로 저장됩니다.",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true, description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true, description = "비밀번호")
        }
    )
    ApiResponse<OrderV1Dto.OrderResponse> placeOrder(
        @Parameter(hidden = true) User user,
        @Schema(description = "주문 요청") OrderV1Dto.PlaceOrderRequest request
    );
}
