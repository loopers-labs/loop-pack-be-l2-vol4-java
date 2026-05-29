package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller {

    private final OrderApplicationService orderApplicationService;

    @GetMapping
    public ApiResponse<Page<OrderAdminV1Dto.OrderResponse>> getOrders(
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        Page<OrderAdminV1Dto.OrderResponse> orders = orderApplicationService.getAllOrders(PageRequest.of(page, size))
            .map(OrderAdminV1Dto.OrderResponse::from);
        return ApiResponse.success(orders);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderAdminV1Dto.OrderResponse> getOrder(@PathVariable @Min(1) Long orderId) {
        return ApiResponse.success(OrderAdminV1Dto.OrderResponse.from(
            orderApplicationService.getOrderForAdmin(orderId)
        ));
    }
}
