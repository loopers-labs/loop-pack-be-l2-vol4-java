package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderCriteria;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller implements OrderAdminV1ApiSpec {

    private final OrderApplicationService orderApplicationService;

    @GetMapping
    @Override
    public ApiResponse<OrderV1Dto.PageResponse> getAllOrders(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageResult<OrderInfo.ListItem> result = orderApplicationService.getAllOrders(
                new OrderCriteria.AdminSearch(page, size));
        return ApiResponse.success(OrderV1Dto.PageResponse.from(result));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.DetailResponse> getOrder(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @PathVariable("orderId") Long orderId
    ) {
        OrderInfo.Detail info = orderApplicationService.getOrder(orderId);
        return ApiResponse.success(OrderV1Dto.DetailResponse.from(info));
    }
}
