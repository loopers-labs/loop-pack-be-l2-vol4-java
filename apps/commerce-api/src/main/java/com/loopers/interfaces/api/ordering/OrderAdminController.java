package com.loopers.interfaces.api.ordering;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.application.ordering.order.OrderQueryService;
import com.loopers.application.ordering.order.OrderResult;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.support.pagination.PageResult;
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
public class OrderAdminController {

    private final OrderQueryService orderQueryService;

    @GetMapping
    public ApiResponse<PageResponse<OrderAdminDto.OrderListItemResponse>> getOrders(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        HeaderValidator.validateAdmin(ldap);
        PageResult<OrderResult.Summary> result = orderQueryService.getAdminOrders(page, size);
        return ApiResponse.success(PageResponse.from(result, OrderAdminDto.OrderListItemResponse::from));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderAdminDto.OrderDetailResponse> getOrder(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @PathVariable Long orderId
    ) {
        HeaderValidator.validateAdmin(ldap);
        OrderResult.Detail result = orderQueryService.getAdminOrder(orderId);
        return ApiResponse.success(OrderAdminDto.OrderDetailResponse.from(result));
    }
}
