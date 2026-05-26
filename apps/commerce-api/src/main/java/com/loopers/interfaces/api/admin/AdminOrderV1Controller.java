package com.loopers.interfaces.api.admin;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class AdminOrderV1Controller {

    private final OrderFacade orderFacade;

    @GetMapping
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        List<OrderV1Dto.OrderResponse> responses = orderFacade.getAllOrders(page, size).stream()
            .map(OrderV1Dto.OrderResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @PathVariable(value = "orderId") Long orderId
    ) {
        OrderInfo info = orderFacade.getOrder(orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
