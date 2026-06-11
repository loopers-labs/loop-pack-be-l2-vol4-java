package com.loopers.interfaces.api.payment;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.payment.PaymentApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.user.AuthUser;
import com.loopers.interfaces.api.user.AuthUserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 API.
 *
 * <p>흐름: POST /orders 로 PENDING 주문 생성 → 프론트가 PG 결제창(인증) →
 * successUrl 에서 paymentKey 수신 → 이 confirm 호출 → 서버가 PG 승인 → 주문 확정.
 * 유저가 보는 "결제 완료" 화면은 이 API 의 성공 응답 이후에 렌더링된다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentApplicationService paymentApplicationService;

    @PostMapping("/confirm")
    public ApiResponse<OrderV1Dto.OrderResponse> confirmPayment(
        @AuthUser AuthUserContext authUser,
        @Valid @RequestBody PaymentV1Dto.ConfirmRequest request
    ) {
        OrderInfo info = paymentApplicationService.confirmPayment(
            authUser.userId(), request.paymentKey(), request.orderId(), request.amount());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
