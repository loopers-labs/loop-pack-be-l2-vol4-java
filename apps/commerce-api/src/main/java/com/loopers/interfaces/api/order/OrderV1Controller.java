package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    /** FR-O-01. 주문 생성 */
    @PostMapping
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @CurrentUser UserModel currentUser,
        @Valid @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        return ApiResponse.success(
            OrderV1Dto.OrderResponse.from(
                orderFacade.createOrder(request.toCommand(currentUser.getId()))
            )
        );
    }

    /** FR-O-02. 본인 주문 목록 조회 */
    @GetMapping
    public ApiResponse<Page<OrderV1Dto.OrderResponse>> getMyOrders(
        @CurrentUser UserModel currentUser,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
            orderFacade.getMyOrders(currentUser.getId(), pageable)
                .map(OrderV1Dto.OrderResponse::from)
        );
    }

    /** FR-O-03. 주문 상세 조회 (본인 것만) */
    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @CurrentUser UserModel currentUser,
        @PathVariable Long orderId
    ) {
        return ApiResponse.success(
            OrderV1Dto.OrderResponse.from(
                orderFacade.getOrder(currentUser.getId(), orderId)
            )
        );
    }
}
