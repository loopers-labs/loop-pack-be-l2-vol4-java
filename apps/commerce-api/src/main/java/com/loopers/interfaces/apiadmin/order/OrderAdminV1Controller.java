package com.loopers.interfaces.apiadmin.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller implements OrderAdminV1ApiSpec {

    private final OrderFacade orderFacade;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<OrderAdminV1Dto.OrderResponse>> getOrders(Pageable pageable) {
        return ApiResponse.success(
                PageResponse.from(OrderAdminV1Dto.OrderResponse.from(orderFacade.getAdminOrders(pageable)))
        );
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderAdminV1Dto.OrderResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.success(OrderAdminV1Dto.OrderResponse.from(orderFacade.getAdminOrder(orderId)));
    }
}
