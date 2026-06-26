package com.loopers.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentReader paymentReader;
    private final PaymentWriter paymentWriter;

    public Optional<Payment> findPayment(Long orderId, String userLoginId) {
        return paymentReader.findPayment(orderId, userLoginId);
    }

    public Payment getPayment(Long orderId, String userLoginId) {
        return paymentReader.getPayment(orderId, userLoginId);
    }

    public Payment getPaymentByOrderId(Long orderId) {
        return paymentReader.getPaymentByOrderId(orderId);
    }

    public List<Payment> findPendingPaymentsForReconciliation(int limit) {
        return paymentReader.findPendingPaymentsForReconciliation(limit);
    }

    public Payment save(Payment payment) {
        return paymentWriter.save(payment);
    }

    public PaymentCreationResult create(Payment payment) {
        return paymentWriter.create(payment);
    }

    public Payment completeIfPending(Payment payment) {
        return paymentWriter.completeIfPending(payment);
    }
}
