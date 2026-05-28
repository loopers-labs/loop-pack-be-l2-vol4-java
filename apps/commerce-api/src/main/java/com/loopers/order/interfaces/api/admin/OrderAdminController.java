package com.loopers.order.interfaces.api.admin;

import com.loopers.common.interfaces.api.AdminAuth;
import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.common.interfaces.api.PagedResponse;
import com.loopers.order.application.OrderFacade;
import com.loopers.order.application.OrderInfo;
import com.loopers.order.interfaces.api.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminController {

    private final OrderFacade orderFacade;

    @GetMapping
    public ApiResponse<PagedResponse<OrderResponse>> getOrders(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminAuth.verify(ldap);
        Page<OrderInfo> result = orderFacade.getAllOrders(page, size);
        return ApiResponse.success(PagedResponse.from(result.map(OrderResponse::from)));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap, @PathVariable("orderId") Long orderId) {
        AdminAuth.verify(ldap);
        return ApiResponse.success(OrderResponse.from(orderFacade.getOrder(orderId)));
    }
}
