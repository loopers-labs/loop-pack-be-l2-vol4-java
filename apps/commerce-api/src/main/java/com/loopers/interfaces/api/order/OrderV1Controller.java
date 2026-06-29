package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderV1Dto.OrderCreateResponse> createOrder(
            @RequestHeader("X-Loopers-UserId") Long userId,
            @RequestBody OrderV1Dto.OrderCreateRequest request
    ) {
        com.loopers.application.order.OrderCreateRequest createRequest = new com.loopers.application.order.OrderCreateRequest(
                request.items().stream()
                        .map(item -> new com.loopers.application.order.OrderCreateRequest.Item(item.productId(), item.quantity()))
                        .toList(),
                request.couponIssueId()
        );
        Long orderId = orderFacade.createOrder(userId, createRequest);
        return ApiResponse.success(new OrderV1Dto.OrderCreateResponse(orderId));
    }
}
