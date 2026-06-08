package com.loopers.domain.payment;

import com.loopers.domain.order.vo.Money;
import com.loopers.domain.payment.enums.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentModel create(Long orderId, Money amount) {
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PENDING)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 진행 중인 결제가 존재합니다.");
        }
        return paymentRepository.save(new PaymentModel(orderId, amount));
    }

    @Transactional(readOnly = true)
    public PaymentModel get(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 결제를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<PaymentModel> getListByOrderId(Long orderId) {
        return paymentRepository.findAllByOrderId(orderId);
    }

    @Transactional
    public PaymentModel approve(Long id) {
        PaymentModel payment = get(id);
        payment.approve();
        return payment;
    }

    @Transactional
    public PaymentModel fail(Long id) {
        PaymentModel payment = get(id);
        payment.fail();
        return payment;
    }

    @Transactional
    public PaymentModel expire(Long id) {
        PaymentModel payment = get(id);
        payment.expire();
        return payment;
    }
}
