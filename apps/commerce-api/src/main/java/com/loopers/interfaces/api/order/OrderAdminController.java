package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderAdminFacade;
import com.loopers.domain.order.OrderModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/orders")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderAdminFacade orderAdminFacade;

    @GetMapping
    public ApiResponse<List<OrderAdminDto.OrderResponse>> getAllOrders(
            @RequestHeader("X-Loopers-Ldap") String ldap
    ) {
        validateAdmin(ldap);
        List<OrderModel> orders = orderAdminFacade.getAllOrders();
        return ApiResponse.success(orders.stream()
                .map(OrderAdminDto.OrderResponse::from)
                .toList());
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderAdminDto.OrderResponse> getOrder(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long orderId
    ) {
        validateAdmin(ldap);
        OrderModel order = orderAdminFacade.getOrder(orderId);
        return ApiResponse.success(OrderAdminDto.OrderResponse.from(order));
    }

    private void validateAdmin(String ldap) {
        if (!"loopers.admin".equals(ldap)) {
            throw new CoreException(ErrorType.NOT_FOUND, "沅뚰븳???놁뒿?덈떎.");
        }
    }
}
