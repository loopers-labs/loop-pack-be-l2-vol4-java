package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.pg.PgCallbackPayload;
import com.loopers.infrastructure.pg.PgPaymentClient;
import com.loopers.infrastructure.pg.PgPaymentRequest;
import com.loopers.infrastructure.pg.PgPaymentResult;
import com.loopers.infrastructure.pg.PgTransactionResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PgPaymentClient pgPaymentClient;
    private final TransactionTemplate transactionTemplate;
    private final CircuitBreaker pgPaymentCircuitBreaker;

    @Value("${pg.callback-url}")
    private String callbackUrl;

    /**
     * 결제 요청.
     * PG 외부 호출이 포함되므로 @Transactional 없이 TransactionTemplate으로 TX를 명시적으로 분리한다.
     *
     * TX 1: 주문 검증 + PaymentModel(PENDING) 선저장 → 커밋
     * [트랜잭션 밖]: CircuitBreaker → PgPaymentClient.request()
     *   - CB CLOSED: 정상 호출
     *   - CB OPEN:   fallback → CoreException → catch 블록 → ABORTED
     * TX 2: PG 응답에 따라 IN_PROGRESS 또는 ABORTED 전환 → 커밋
     */
    public PaymentInfo requestPayment(PaymentCommand command) {
        // TX 1: 주문 검증 + PENDING 선저장
        PaymentModel payment = transactionTemplate.execute(status -> {
            OrderModel order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

            if (!order.getUserId().equals(command.userId())) {
                throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
            }
            if (order.getStatus() != OrderStatus.PENDING) {
                throw new CoreException(ErrorType.BAD_REQUEST, "결제 가능한 주문 상태가 아닙니다.");
            }

            paymentRepository.findByOrderId(command.orderId()).ifPresent(existing -> {
                if (existing.getStatus() == PaymentStatus.IN_PROGRESS
                    || existing.getStatus() == PaymentStatus.SUCCESS) {
                    throw new CoreException(ErrorType.CONFLICT, "이미 처리 중이거나 완료된 결제가 있습니다.");
                }
            });

            PaymentModel newPayment = new PaymentModel(
                command.orderId(),
                command.userId(),
                command.cardType(),
                command.cardNo(),
                order.getTotalAmount()
            );
            return paymentRepository.save(newPayment);
        });

        // PG 호출 (트랜잭션 밖 — CircuitBreaker 보호)
        PgPaymentRequest pgRequest = PgPaymentRequest.from(payment, callbackUrl);

        try {
            PgPaymentResult pgResult = pgPaymentCircuitBreaker.run(
                () -> pgPaymentClient.request(pgRequest),
                this::pgRequestFallback
            );

            // TX 2-성공: PENDING → IN_PROGRESS
            return transactionTemplate.execute(status -> {
                PaymentModel found = paymentRepository.findById(payment.getId()).orElseThrow();
                found.startProgress(pgResult.transactionKey());
                return PaymentInfo.from(paymentRepository.save(found));
            });

        } catch (Exception e) {
            // TX 2-실패: PENDING → ABORTED
            transactionTemplate.executeWithoutResult(status -> {
                PaymentModel found = paymentRepository.findById(payment.getId()).orElseThrow();
                found.markAborted();
                paymentRepository.save(found);
            });
            // fallback이 던진 CoreException은 그대로 전파, 그 외는 래핑
            if (e instanceof CoreException ce) {
                throw ce;
            }
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 요청에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    /**
     * CB Fallback — OPEN 상태이거나 예외 발생 시 호출.
     * 여기서 던진 예외는 catch 블록으로 전파되어 payment를 ABORTED로 처리한다.
     */
    private PgPaymentResult pgRequestFallback(Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.warn("PG 결제 CB OPEN — 서킷 차단 중, orderId 요청 차단됨");
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 서비스가 일시적으로 사용 불가합니다. 잠시 후 다시 시도해주세요.");
        }
        log.warn("PG 결제 요청 실패 — {}", t.getMessage());
        throw new CoreException(ErrorType.BAD_REQUEST, "결제 요청에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }

    /**
     * PG 콜백 수신 처리.
     * 외부 호출 없이 DB 작업만 하므로 @Transactional 적용.
     */
    @Transactional
    public void handleCallback(PgCallbackPayload payload) {
        PaymentModel payment = paymentRepository.findByPgTransactionId(payload.transactionKey())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));

        if ("SUCCESS".equals(payload.status())) {
            payment.markSuccess();
            OrderModel order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
            order.confirm();
        } else {
            payment.markFailed(payload.reason());
        }
    }

    /**
     * PG 상태 수동 동기화.
     * 콜백 미수신(IN_PROGRESS) 또는 타임아웃(ABORTED) 결제건에 대해 PG에 직접 상태를 조회해 반영한다.
     *
     * TX 1: 결제 조회 + 소유권/상태 검증
     * [트랜잭션 밖]: PG 상태 조회
     * TX 2: PG 응답 기반 상태 반영
     */
    public PaymentInfo syncPayment(Long paymentId, Long requestUserId) {
        // TX 1: 결제 조회
        PaymentModel snapshot = transactionTemplate.execute(status ->
            paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."))
        );

        if (!snapshot.getUserId().equals(requestUserId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다.");
        }
        // 이미 최종 상태 → 동기화 불필요
        if (snapshot.getStatus() == PaymentStatus.SUCCESS
            || snapshot.getStatus() == PaymentStatus.FAILED) {
            return PaymentInfo.from(snapshot);
        }

        // PG 상태 조회 (트랜잭션 밖)
        Optional<PgTransactionResponse> pgStatus = queryPgStatus(snapshot);

        // TX 2: PG 응답 반영
        return transactionTemplate.execute(status -> {
            PaymentModel payment = paymentRepository.findById(paymentId).orElseThrow();
            pgStatus.ifPresent(pg -> {
                payment.applyPgResult(pg.transactionKey(), pg.status(), pg.reason());
                if (payment.getStatus() == PaymentStatus.SUCCESS) {
                    OrderModel order = orderRepository.findById(payment.getOrderId())
                        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
                    order.confirm();
                }
            });
            return PaymentInfo.from(paymentRepository.save(payment));
        });
    }

    /**
     * 결제 상태에 따라 PG 조회 방식을 선택한다.
     * IN_PROGRESS: pgTransactionId로 직접 조회
     * ABORTED/PENDING: orderId로 조회 (PG 도달 여부 불명)
     */
    private Optional<PgTransactionResponse> queryPgStatus(PaymentModel payment) {
        try {
            if (payment.getPgTransactionId() != null) {
                return pgPaymentClient.getStatus(payment.getPgTransactionId(), payment.getUserId());
            }
            String paddedOrderId = String.format("%010d", payment.getOrderId());
            return pgPaymentClient.findByOrderId(paddedOrderId, payment.getUserId());
        } catch (Exception e) {
            log.warn("PG 상태 조회 실패 — paymentId={}, reason={}", payment.getId(), e.getMessage());
            return Optional.empty();
        }
    }
}
