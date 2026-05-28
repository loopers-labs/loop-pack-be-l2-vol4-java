package com.loopers.interfaces.api.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.user.AdminAuth;
import com.loopers.interfaces.api.user.AuthHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller {

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<PageResponse<OrderAdminV1Dto.OrderListResponse>> getOrders(
            @RequestHeader(AuthHeaders.LDAP) String ldap,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AdminAuth.validate(ldap);
        return ApiResponse.success(PageResponse.from(
                orderService.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .map(OrderAdminV1Dto.OrderListResponse::from)
        ));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderAdminV1Dto.OrderDetailResponse> getOrder(
            @RequestHeader(AuthHeaders.LDAP) String ldap,
            @PathVariable Long orderId
    ) {
        AdminAuth.validate(ldap);
        OrderModel order = orderService.getByIdWithItems(orderId);
        return ApiResponse.success(OrderAdminV1Dto.OrderDetailResponse.from(order));
    }
}
