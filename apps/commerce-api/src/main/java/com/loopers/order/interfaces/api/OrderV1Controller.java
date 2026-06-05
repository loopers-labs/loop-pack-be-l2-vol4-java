package com.loopers.order.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.order.application.OrderResult;
import com.loopers.order.application.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderService orderService;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Response.Detail> create(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody OrderV1Request.Create request
    ) {
        OrderResult.Detail result = orderService.create(request.toCommand(userId));
        return ApiResponse.success(OrderV1Response.Detail.from(result));
    }

    @GetMapping
    @Override
    public ApiResponse<List<OrderV1Response.Summary>> getMyOrders(
        @AuthenticationPrincipal Long userId
    ) {
        List<OrderV1Response.Summary> responses = orderService.getMyOrders(userId).stream()
                .map(OrderV1Response.Summary::from)
                .toList();
        return ApiResponse.success(responses);
    }
}
