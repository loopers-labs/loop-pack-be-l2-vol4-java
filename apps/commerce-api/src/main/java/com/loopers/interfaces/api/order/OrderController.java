package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderDto.OrderResponse> createOrder(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody OrderDto.CreateOrderRequest request
    ) {
        OrderInfo info = orderFacade.createOrder(principal.getId(), request.toOrderRequests());
        return ApiResponse.success(OrderDto.OrderResponse.from(info));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderResponse> getOrder(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.getOrder(orderId, principal.getId());
        return ApiResponse.success(OrderDto.OrderResponse.from(info));
    }

    @GetMapping
    public ApiResponse<List<OrderDto.OrderResponse>> getOrders(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startAt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endAt
    ) {
        List<OrderInfo> orders = orderFacade.getOrders(principal.getId(), startAt, endAt);
        return ApiResponse.success(orders.stream().map(OrderDto.OrderResponse::from).toList());
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderDto.OrderResponse> cancelOrder(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.cancelOrder(orderId, principal.getId());
        return ApiResponse.success(OrderDto.OrderResponse.from(info));
    }
}
