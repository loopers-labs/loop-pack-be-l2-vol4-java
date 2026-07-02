package com.loopers.tddstudy.interfaces.api.payment;

import com.loopers.tddstudy.application.order.OrderItemRequest;
import com.loopers.tddstudy.application.order.OrderService;
import com.loopers.tddstudy.domain.order.Order;
import com.loopers.tddstudy.domain.user.User;
import com.loopers.tddstudy.dto.LoginRequest;
import com.loopers.tddstudy.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final OrderService orderService;
    private final UserService userService;

    public PaymentV1Controller(OrderService orderService, UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<PaymentV1Dto.PaymentResponse> requestPayment(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        User user = userService.login(new LoginRequest(loginId, loginPw));

        Order order = orderService.createOrder(
                user.getId(),
                List.of(new OrderItemRequest(request.productId(), 1)),
                null
        );

        String message = "PENDING".equals(order.getStatus())
                ? "결제 처리중입니다."
                : "결제에 실패했습니다.";

        return ResponseEntity.ok(
                new PaymentV1Dto.PaymentResponse(order.getId(), order.getStatus(), message)
        );
    }
}
