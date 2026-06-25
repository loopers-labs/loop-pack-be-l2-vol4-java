package com.loopers.payment.application;

import com.loopers.order.domain.OrderModel;
import com.loopers.order.domain.OrderRepository;
import com.loopers.payment.domain.PaymentModel;
import com.loopers.payment.domain.PaymentRepository;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.infrastructure.pg.PgPaymentClient;
import com.loopers.payment.infrastructure.pg.PgPaymentClientDto;
import com.loopers.payment.infrastructure.pg.PgRetriableException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PgPaymentClient pgPaymentClient;
    private final PlatformTransactionManager transactionManager;

    @Value("${pg.callback-url}")
    private String callbackUrl;

    @Value("${pg.retry-max-attempts:3}")
    private int retryMaxAttempts;

    private TransactionTemplate transactionTemplate;

    @PostConstruct
    void init() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PaymentInfo requestPayment(Long userId, String loginId, Long orderId, String cardType, String cardNo) {
        // TX1: 비관적 락 획득 → startPayment 커밋 → 커넥션 반환
        OrderModel order = transactionTemplate.execute(status -> {
            OrderModel o = orderRepository.findWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
            if (!o.getUserId().equals(userId)) {
                throw new CoreException(ErrorType.FORBIDDEN, "접근 권한이 없습니다.");
            }
            o.startPayment();
            return orderRepository.save(o);
        });

        // PG 호출 — 트랜잭션 밖, 커넥션 미점유
        // 일시적 실패(타임아웃·네트워크·PG 500)는 200ms 간격으로 최대 2회 재시도한다.
        // 멱등키가 OrderModel에 저장돼 있어 retry 시 같은 키를 재사용하므로 중복 결제가 발생하지 않는다.
        // CB OPEN(SERVICE_UNAVAILABLE)은 retry 없이 즉시 실패 처리한다.
        PgPaymentClientDto.TransactionResponse pgResponse;
        try {
            pgResponse = requestPaymentWithRetry(loginId, order, orderId, cardType, cardNo);
        } catch (Exception pgException) {
            // TX C: 모든 재시도 소진 후 최종 실패 → Order를 PAYMENT_FAILED로 전이 (재결제 허용 상태)
            transactionTemplate.execute(status -> {
                OrderModel o = orderRepository.find(orderId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
                o.failPayment();
                return orderRepository.save(o);
            });
            throw pgException;
        }

        // TX2: 결제 기록 저장
        return transactionTemplate.execute(status -> {
            PaymentModel payment = new PaymentModel(orderId, pgResponse.transactionKey(), cardType, order.getFinalAmount(), loginId);
            return PaymentInfo.from(paymentRepository.save(payment));
        });
    }

    private PgPaymentClientDto.TransactionResponse requestPaymentWithRetry(
        String loginId, OrderModel order, Long orderId, String cardType, String cardNo
    ) {
        int waitMillis = 200;
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                return pgPaymentClient.requestPayment(
                    loginId,
                    new PgPaymentClientDto.PaymentRequest(
                        order.getIdempotencyKey(),
                        String.valueOf(orderId),
                        cardType,
                        cardNo,
                        order.getFinalAmount(),
                        callbackUrl
                    )
                );
            } catch (PgRetriableException e) {
                if (attempt == retryMaxAttempts) {
                    throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "결제 시스템이 일시적으로 불가합니다.");
                }
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "결제 요청이 중단되었습니다.");
                }
            }
        }
        throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "결제 시스템이 일시적으로 불가합니다.");
    }

    @Transactional
    public void recoverPayment(Long orderId, Long userId, String loginId) {
        // [fix] 동시 복구 요청 시 PaymentModel 중복 생성 방지 — isEmpty 체크 전에 락 선점
        OrderModel order = orderRepository.findWithLock(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "접근 권한이 없습니다.");
        }

        Optional<PaymentModel> existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isEmpty()) {
            // [fix] 타임아웃 등으로 PaymentModel이 로컬에 저장되지 못한 경우, orderId 기준으로 PG에 실제 거래가 있었는지 확인해 동기화
            recoverFromPgByOrderId(order, loginId);
            return;
        }

        PaymentModel payment = existingPayment.get();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        PgPaymentClientDto.TransactionResponse pgResponse = pgPaymentClient.getTransaction(loginId, payment.getTransactionKey());
        applyPgResult(payment, order, pgResponse.status());
    }

    private void recoverFromPgByOrderId(OrderModel order, String loginId) {
        PgPaymentClientDto.OrderTransactionsResponse pgOrderResponse;
        try {
            pgOrderResponse = pgPaymentClient.getTransactionsByOrder(loginId, order.getId().toString());
        } catch (CoreException e) {
            if (e.getErrorType() == ErrorType.NOT_FOUND) {
                return;
            }
            throw e;
        }

        String latestTransactionKey = pgOrderResponse.transactions().get(0).transactionKey();
        PgPaymentClientDto.TransactionResponse pgResponse = pgPaymentClient.getTransaction(loginId, latestTransactionKey);

        PaymentModel payment = new PaymentModel(order.getId(), latestTransactionKey, "UNKNOWN", order.getFinalAmount(), loginId);
        // [fix] BaseEntity.id 기본값이 0L(null 아님)이라, save() 반환값을 쓰지 않으면 다음 save()도 새 INSERT로 처리되어
        // 같은 transactionKey가 중복 삽입됨 — 반환된(영속화된) 인스턴스로 교체
        payment = paymentRepository.save(payment);
        applyPgResult(payment, order, pgResponse.status());
    }

    @Transactional
    public void handleCallback(String transactionKey, Long orderId) {
        Optional<PaymentModel> existingPayment = paymentRepository.findByTransactionKey(transactionKey);

        if (existingPayment.isEmpty()) {
            // [fix] 타임아웃으로 PaymentModel이 없는 경우 콜백이 도착해도 NOT_FOUND로 버려지던 버그 수정
            OrderModel order = orderRepository.find(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
            recoverFromPgByTransactionKey(order, transactionKey);
            return;
        }

        PaymentModel payment = existingPayment.get();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        OrderModel order = orderRepository.find(payment.getOrderId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        // [fix] 콜백 발신지를 검증할 방법이 없어, 바디의 status를 그대로 믿는 대신 PG에 재조회한 실제 상태를 사용
        PgPaymentClientDto.TransactionResponse pgResponse = pgPaymentClient.getTransaction(payment.getLoginId(), transactionKey);
        applyPgResult(payment, order, pgResponse.status());
    }

    private void recoverFromPgByTransactionKey(OrderModel order, String transactionKey) {
        PgPaymentClientDto.TransactionResponse pgResponse;
        try {
            pgResponse = pgPaymentClient.getTransaction(order.getLoginId(), transactionKey);
        } catch (CoreException e) {
            if (e.getErrorType() == ErrorType.NOT_FOUND) {
                return;
            }
            throw e;
        }

        PaymentModel payment = new PaymentModel(order.getId(), transactionKey, "UNKNOWN", order.getFinalAmount(), order.getLoginId());
        payment = paymentRepository.save(payment);
        applyPgResult(payment, order, pgResponse.status());
    }

    private void applyPgResult(PaymentModel payment, OrderModel order, String pgStatus) {
        if ("PENDING".equals(pgStatus)) {
            return;
        }

        if ("SUCCESS".equals(pgStatus)) {
            payment.confirm();
            order.confirm();
        } else {
            payment.fail();
            order.failPayment();
        }

        paymentRepository.save(payment);
        orderRepository.save(order);
    }
}
