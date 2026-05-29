package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller implements OrderAdminV1ApiSpec {

    private final OrderFacade orderFacade;

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.AdminOrderResponse> get(@PathVariable UUID orderId) {
        return ApiResponse.success(OrderV1Dto.AdminOrderResponse.from(orderFacade.getById(orderId)));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<OrderV1Dto.AdminOrderResponse>> getList(Pageable pageable) {
        return ApiResponse.success(
            PageResponse.from(orderFacade.getList(pageable).map(OrderV1Dto.AdminOrderResponse::from))
        );
    }
}
