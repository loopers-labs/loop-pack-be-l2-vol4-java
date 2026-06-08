package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller {

    private final OrderApplicationService orderApplicationService;

    @GetMapping
    public ApiResponse<List<OrderAdminV1Dto.OrderResponse>> getAllOrders(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<OrderInfo> infos = orderApplicationService.getAllOrders(page, size);
        List<OrderAdminV1Dto.OrderResponse> responses = infos.stream()
            .map(OrderAdminV1Dto.OrderResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderAdminV1Dto.OrderResponse> getOrder(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderApplicationService.getOrderAdmin(orderId);
        return ApiResponse.success(OrderAdminV1Dto.OrderResponse.from(info));
    }
}
