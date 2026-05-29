package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class AdminOrderController {

    private final OrderFacade orderFacade;

    @GetMapping
    public ApiResponse<List<OrderDto.OrderResponse>> getAllOrders(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        List<OrderDto.OrderResponse> orders = orderFacade.getAllOrders(startAt, endAt).stream()
            .map(OrderDto.OrderResponse::from)
            .toList();
        return ApiResponse.success(orders);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderResponse> getOrderDetail(@PathVariable Long orderId) {
        OrderInfo info = orderFacade.getOrderDetailForAdmin(orderId);
        return ApiResponse.success(OrderDto.OrderResponse.from(info));
    }
}
