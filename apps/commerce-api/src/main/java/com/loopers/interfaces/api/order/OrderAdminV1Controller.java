package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller {

    private final OrderFacade orderFacade;

    @GetMapping
    public ApiResponse<Page<OrderV1Dto.OrderResponse>> getAllOrders(Pageable pageable) {
        return ApiResponse.success(orderFacade.getAllOrders(pageable).map(OrderV1Dto.OrderResponse::from));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(orderFacade.getOrder(orderId)));
    }
}
