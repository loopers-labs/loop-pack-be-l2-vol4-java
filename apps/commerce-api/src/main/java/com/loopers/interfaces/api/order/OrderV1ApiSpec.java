package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Order", description = "주문 API")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 생성", description = "여러 상품을 담아 주문을 생성하고 재고를 차감한다.")
    ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestBody OrderV1Dto.CreateOrderRequest request
    );
}
