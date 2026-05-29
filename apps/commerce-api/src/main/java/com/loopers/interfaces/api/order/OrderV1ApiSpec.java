package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order V1 API", description = "주문 도메인 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 생성", description = "헤더로 식별한 유저가 여러 상품을 주문합니다. 재고를 차감합니다.")
    ApiResponse<OrderV1Dto.OrderResponse> createOrder(AuthHeaders auth, OrderV1Dto.CreateOrderRequest request);

    @Operation(summary = "주문 단건 조회", description = "헤더로 식별한 유저 본인의 주문을 조회합니다.")
    ApiResponse<OrderV1Dto.OrderResponse> getOrder(AuthHeaders auth, Long orderId);
}
