package com.loopers.tddstudy.interfaces.api.order;

import com.loopers.tddstudy.application.order.OrderItemRequest;
import com.loopers.tddstudy.application.order.OrderService;
import com.loopers.tddstudy.domain.order.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderService orderService;

    public OrderV1Controller(OrderService orderService) {
        this.orderService = orderService;
    }

    // 토큰 검증은 Interceptor(게이트)가 이미 통과시킨 뒤라, 여기선 주문 로직만
    @PostMapping
    public ResponseEntity<OrderV1Dto.OrderResponse> createOrder(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody OrderV1Dto.OrderRequest request) {

        List<OrderItemRequest> items = request.items().stream()
                .map(i -> new OrderItemRequest(i.productId(), i.quantity()))
                .toList();

        Order order = orderService.createOrder(userId, items, request.couponId());  // ← 여기서 R7 파이프라인 발동

        return ResponseEntity.ok(OrderV1Dto.OrderResponse.from(order));
    }
}
