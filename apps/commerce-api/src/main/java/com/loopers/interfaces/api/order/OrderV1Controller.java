package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemRequest;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderV1Dto.OrderResponse> placeOrder(
            @RequestHeader("X-USER-ID") Long memberId,
            @RequestBody OrderV1Dto.PlaceOrderRequest request
    ) {
        List<OrderItemRequest> items = request.items().stream()
                .map(item -> new OrderItemRequest(item.productId(), item.quantity()))
                .toList();

        OrderInfo info = orderFacade.placeOrder(memberId, items, request.couponId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
