package com.loopers.application.payment;

import com.loopers.domain.payment.model.CardType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentApplicationService paymentApplicationService;

    public PaymentInfo requestPayment(String loginId, Long orderId, CardType cardType, String cardNo) {
        return paymentApplicationService.requestPayment(loginId, orderId, cardType, cardNo);
    }
}
