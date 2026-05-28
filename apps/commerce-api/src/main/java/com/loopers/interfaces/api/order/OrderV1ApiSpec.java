package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order API", description = "주문 API")
public interface OrderV1ApiSpec {

    @Operation(
        summary = "주문 생성",
        description = "로그인한 회원이 상품 목록과 수량으로 주문을 생성합니다. 같은 상품이 여러 라인으로 들어오면 수량을 합산합니다."
    )
    ApiResponse<OrderV1Dto.OrderResponse> placeOrder(LoginUser loginUser, OrderV1Dto.PlaceOrderRequest request);
}
