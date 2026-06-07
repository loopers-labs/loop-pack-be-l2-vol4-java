package com.loopers.interfaces.api.order;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.order.OrderAdminInfo;
import com.loopers.application.order.OrderAdminSummaryInfo;
import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller implements OrderAdminV1ApiSpec {

    private final OrderFacade orderFacade;

    @Override
    @GetMapping
    public ApiResponse<OrderAdminV1Dto.PageResponse> readOrders(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<OrderAdminSummaryInfo> ordersInfo = orderFacade.readOrders(page, size);

        return ApiResponse.success(OrderAdminV1Dto.PageResponse.from(ordersInfo));
    }

    @Override
    @GetMapping("/{orderId}")
    public ApiResponse<OrderAdminV1Dto.DetailResponse> readOrder(@PathVariable Long orderId) {
        OrderAdminInfo orderInfo = orderFacade.readOrder(orderId);

        return ApiResponse.success(OrderAdminV1Dto.DetailResponse.from(orderInfo));
    }
}
