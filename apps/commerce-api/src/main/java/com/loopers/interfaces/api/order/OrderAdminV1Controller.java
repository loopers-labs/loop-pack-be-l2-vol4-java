package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller implements OrderAdminV1ApiSpec {

    private final OrderApplicationService orderApplicationService;

    @GetMapping
    public ApiResponse<PageResult<OrderV1Dto.AdminOrderResponse>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                PageResult.from(
                        orderApplicationService.getAdminOrders(PageRequest.of(page, size))
                                .map(OrderV1Dto.AdminOrderResponse::from)
                )
        );
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.AdminOrderResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.success(
                OrderV1Dto.AdminOrderResponse.from(orderApplicationService.getAdminOrder(orderId))
        );
    }
}
