package com.loopers.interfaces.api.order.admin;

import com.loopers.application.order.AdminOrderInfo;
import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class AdminOrderV1Controller implements AdminOrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @GetMapping
    @Override
    public ApiResponse<AdminOrderV1Dto.PageResponse> getAllOrders(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<AdminOrderInfo> result = orderFacade.getAllOrders(page, size);
        return ApiResponse.success(AdminOrderV1Dto.PageResponse.from(result));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<AdminOrderV1Dto.AdminOrderDetail> getOrder(
        @PathVariable("orderId") Long orderId
    ) {
        AdminOrderInfo info = orderFacade.getOrder(orderId);
        return ApiResponse.success(AdminOrderV1Dto.AdminOrderDetail.from(info));
    }
}
