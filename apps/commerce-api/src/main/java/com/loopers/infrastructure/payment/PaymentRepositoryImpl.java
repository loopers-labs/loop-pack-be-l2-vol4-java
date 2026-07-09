package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static com.loopers.domain.payment.QPaymentModel.paymentModel;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public PaymentModel save(PaymentModel payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<PaymentModel> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId);
    }

    @Override
    public List<PaymentModel> findRecoverable(int maxRecoveryAttempts, ZonedDateTime createdBefore) {
        return queryFactory
                .selectFrom(paymentModel)
                .where(
                        paymentModel.status.eq(PaymentStatus.PENDING),
                        paymentModel.pgRequestAttempted.isTrue(),
                        paymentModel.recoveryAttempts.lt(maxRecoveryAttempts),
                        paymentModel.createdAt.lt(createdBefore),
                        paymentModel.deletedAt.isNull()
                )
                .fetch();
    }
}
