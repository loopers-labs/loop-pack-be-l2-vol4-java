package com.loopers.application.payment.payment;

import com.loopers.domain.payment.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentWorker {

    private static final int BATCH_SIZE = 100;

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Transactional
    public List<PaymentProcessResult> processRequestedPayments() {
        return paymentRepository.findRequestedPaymentsForUpdate(BATCH_SIZE)
            .stream()
            .map(payment -> paymentService.processRequestedPayment(payment.getOrderId()))
            .toList();
    }
}
