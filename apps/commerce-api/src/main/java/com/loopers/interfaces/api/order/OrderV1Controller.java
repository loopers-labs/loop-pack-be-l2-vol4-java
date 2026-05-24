package com.loopers.interfaces.api.order;

import com.loopers.application.order.GetMyOrdersCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @AuthenticationPrincipal Long userId,
        @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        OrderInfo info = orderFacade.createOrder(request.toCommand(userId));
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderV1Dto.OrderResponse>> getOrders(
        @AuthenticationPrincipal Long userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        PageResult<OrderInfo> orders = orderFacade.getMyOrders(new GetMyOrdersCommand(userId, page, size, startAt, endAt));
        return ApiResponse.success(PageResponse.from(orders.map(OrderV1Dto.OrderResponse::from)));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.getMyOrderDetail(orderId, userId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
