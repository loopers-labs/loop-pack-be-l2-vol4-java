package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderCheckoutRequest;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping("/checkout")
    public ApiResponse<OrderV1Dto.CheckoutResponse> checkout(
            @RequestHeader("X-Loopers-UserId") Long userId,
            @RequestBody OrderV1Dto.CheckoutRequest request
    ) {
        OrderCheckoutRequest checkoutRequest = new OrderCheckoutRequest(
                request.items().stream()
                        .map(item -> new OrderCheckoutRequest.Item(item.productId(), item.quantity()))
                        .toList(),
                request.couponIssueId(),
                request.paymentMethod()
        );

        Long orderId = orderFacade.checkout(userId, checkoutRequest);
        return ApiResponse.success(new OrderV1Dto.CheckoutResponse(orderId));
    }
}
