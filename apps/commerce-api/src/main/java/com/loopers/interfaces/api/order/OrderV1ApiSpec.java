package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order V1 API", description = "주문 관련 API")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 생성", description = "여러 상품을 한 번에 주문한다. 재고 부족 시 전체 실패(All-or-Nothing).")
    ApiResponse<OrderV1Dto.OrderResponse> createOrder(Long userId, OrderV1Dto.OrderRequest request);
}
