package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.order.dto.OrderV1Response;
import com.loopers.interfaces.api.order.dto.PlaceOrderV1Request;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order V1 API", description = "Loopers 주문 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(
        summary = "주문 생성",
        description = "상품 항목으로 주문을 생성합니다. 쿠폰은 주문 1건당 1장을 주문 전체 금액에 적용할 수 있습니다."
    )
    ApiResponse<OrderV1Response> placeOrder(AuthUser authUser, PlaceOrderV1Request request);
}
