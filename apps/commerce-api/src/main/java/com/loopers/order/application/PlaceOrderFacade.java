package com.loopers.order.application;

import com.loopers.common.domain.Money;
import com.loopers.payment.application.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class PlaceOrderFacade {

    private final PlaceOrderService placeOrderService;
    private final PaymentService paymentService;
    private final OrderCompensationService orderCompensationService;
    private final OrderNumberGenerator orderNumberGenerator;

    public OrderResult.Detail place(OrderCommand.Create command) {
        String orderNumber = orderNumberGenerator.generate();
        OrderResult.Detail order = placeOrderService.createPendingOrder(command, orderNumber);
        try {
            paymentService.pay(order.orderId(), Money.of(order.finalAmount()));
            log.info("결제 완료 orderId={} finalAmount={}", order.orderId(), order.finalAmount());
        } catch (RuntimeException e) {
            log.warn("결제 실패, 보상 시작 orderId={} : {}", order.orderId(), e.getMessage());
            orderCompensationService.compensate(order.orderId());
            throw e;
        }
        return order;
    }
}
