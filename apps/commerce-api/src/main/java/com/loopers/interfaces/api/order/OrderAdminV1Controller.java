package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller {

    private final OrderFacade orderFacade;

    /** FR-OA-01. 주문 목록 조회 (어드민) */
    @GetMapping
    public ApiResponse<Page<OrderV1Dto.OrderResponse>> getAllOrders(
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
            orderFacade.getAllOrders(pageable).map(OrderV1Dto.OrderResponse::from)
        );
    }

    /** FR-OA-02. 주문 상세 조회 (어드민) */
    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.success(
            OrderV1Dto.OrderResponse.from(orderFacade.getOrderByAdmin(orderId))
        );
    }
}
