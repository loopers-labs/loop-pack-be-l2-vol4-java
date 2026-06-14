package com.loopers.order.interfaces.api;

import com.loopers.order.application.OrderAdminFacade;
import com.loopers.order.application.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller {

    private final OrderAdminFacade orderAdminFacade;

    @GetMapping
    public ApiResponse<PageResponse<OrderAdminV1Dto.OrderResponse>> getOrders(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<OrderAdminV1Dto.OrderResponse> orders = orderAdminFacade.getOrders(page, size)
            .map(OrderAdminV1Dto.OrderResponse::from);
        return ApiResponse.success(PageResponse.from(orders));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderAdminV1Dto.OrderResponse> getOrder(@PathVariable Long orderId) {
        OrderInfo info = orderAdminFacade.getOrder(orderId);
        return ApiResponse.success(OrderAdminV1Dto.OrderResponse.from(info));
    }
}
