package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        AuthHeaders auth,
        @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        OrderInfo info = orderFacade.createOrder(auth.loginId(), request.toCommand());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        AuthHeaders auth,
        @PathVariable(value = "orderId") Long orderId
    ) {
        OrderInfo info = orderFacade.getOrder(auth.loginId(), orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
