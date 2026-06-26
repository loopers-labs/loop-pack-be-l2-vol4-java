package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주문 흐름을 조율하는 오케스트레이터. 트랜잭션을 직접 걸지 않는다.
 * 원자적 주문생성(재고+쿠폰+주문 저장)은 OrderRegistrationService 의 트랜잭션에 위임하고,
 * 외부 결제(PG) 호출은 그 트랜잭션이 커밋된 뒤 트랜잭션 밖에서 수행한다(외부 지연이 DB 커넥션을 점유하지 않도록).
 */
@Component
public class OrderFacade {
    private final OrderRegistrationService orderRegistrationService;
    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final String callbackUrl;

    public OrderFacade(OrderRegistrationService orderRegistrationService,
                       PaymentGateway paymentGateway,
                       PaymentRepository paymentRepository,
                       @Value("${payment.callback-url}") String callbackUrl) {
        this.orderRegistrationService = orderRegistrationService;
        this.paymentGateway = paymentGateway;
        this.paymentRepository = paymentRepository;
        this.callbackUrl = callbackUrl;
    }

    public OrderInfo place(Long userId, List<OrderLineCommand> commands, Long couponId, CardType cardType, String cardNo) {
        Order order = orderRegistrationService.register(userId, commands, couponId);
        PaymentGateway.PaymentResult result = requestPayment(order, userId, cardType, cardNo);
        paymentRepository.save(new Payment(
            order.getId(), userId, result.transactionKey(),
            order.getPaymentAmount(), result.status(), result.reason()));
        return OrderInfo.from(order);
    }

    private PaymentGateway.PaymentResult requestPayment(Order order, Long userId, CardType cardType, String cardNo) {
        return paymentGateway.requestPayment(new PaymentGateway.PaymentRequest(
            String.valueOf(userId),
            String.format("%06d", order.getId()),   // 시뮬레이터는 orderId 를 6자리 이상 문자열로 요구
            cardType.name(),
            cardNo,
            order.getPaymentAmount().getAmount().longValue(),
            callbackUrl
        ));
    }
}
