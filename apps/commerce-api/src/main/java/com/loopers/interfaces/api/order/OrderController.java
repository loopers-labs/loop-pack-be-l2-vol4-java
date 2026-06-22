package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderFacade orderFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDto.OrderResponse> createOrder(
        @Valid @RequestBody OrderDto.CreateRequest request,
        @RequestAttribute("userId") Long userId
    ) {
        OrderInfo info = orderFacade.createOrder(userId, OrderDto.toCommands(request.items()), request.couponId());
        return ApiResponse.success(OrderDto.OrderResponse.from(info));
    }

    @DeleteMapping("/{orderId}")
    public ApiResponse<Void> cancelOrder(
        @PathVariable Long orderId,
        @RequestAttribute("userId") Long userId
    ) {
        orderFacade.cancelOrder(orderId, userId);
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<List<OrderDto.OrderResponse>> getOrders(
        @RequestAttribute("userId") Long userId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        List<OrderDto.OrderResponse> orders = orderFacade.getOrders(userId, startAt, endAt).stream()
            .map(OrderDto.OrderResponse::from)
            .toList();
        return ApiResponse.success(orders);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderResponse> getOrderDetail(
        @PathVariable Long orderId,
        @RequestAttribute("userId") Long userId
    ) {
        OrderInfo info = orderFacade.getOrderDetail(orderId, userId);
        return ApiResponse.success(OrderDto.OrderResponse.from(info));
    }

    @PatchMapping("/{orderId}/confirm")
    public ApiResponse<OrderDto.OrderResponse> confirmOrder(
        @PathVariable Long orderId,
        @RequestAttribute("userId") Long userId
    ) {
        OrderInfo info = orderFacade.confirmOrder(orderId, userId);
        return ApiResponse.success(OrderDto.OrderResponse.from(info));
    }
}
