package com.loopers.order.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.order.application.OrderAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/orders")
public class OrderAdminV1Controller implements OrderAdminV1ApiSpec {

    private final OrderAdminService orderAdminService;

    @GetMapping
    @Override
    public ApiResponse<List<OrderV1Response.Summary>> getAllOrders() {
        List<OrderV1Response.Summary> responses = orderAdminService.getAllOrders().stream()
                .map(OrderV1Response.Summary::from)
                .toList();
        return ApiResponse.success(responses);
    }
}
