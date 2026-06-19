package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        List<OrderFacade.OrderRequest> orderRequests = request.items().stream()
            .map(item -> new OrderFacade.OrderRequest(item.productId(), item.quantity()))
            .toList();
            
        OrderInfo info = orderFacade.createOrder(loginId, loginPw, orderRequests, request.userCouponId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
