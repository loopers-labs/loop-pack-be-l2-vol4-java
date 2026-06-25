package com.loopers.application.payment;

import com.loopers.application.user.UserFacade;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 결제 시작 흐름 (03 §3.3). 주문 생성(placeOrder)과 분리된 별도 진입점.
 * <p>
 * 흐름: 인증 → 주문 소유/상태 검증 → 멱등 가드 → PENDING 결제 저장 → PG 요청(DB tx 밖, 재시도 포함)
 *  → 거래키 저장. 실제 승인/거절은 PG 콜백 또는 reconcile에서 확정한다(여기서는 주문을 건드리지 않는다).
 */
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    /** pg-simulator는 callbackUrl이 http://localhost:8080 으로 시작할 것을 요구한다. */
    static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";

    private final UserFacade userFacade;
    private final OrderService orderService;
    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final PaymentConfirmer paymentConfirmer;

    public PaymentInfo pay(String loginId, String loginPw, Long orderId, CardType cardType, String cardNo) {
        Long userId = userFacade.authenticate(loginId, loginPw);

        OrderModel order = orderService.getOrder(orderId);
        // 타인 주문은 존재를 숨긴다(NOT_FOUND로 통일) — 정보 노출 방지.
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT,
                    "결제할 수 없는 주문 상태입니다. (현재: " + order.getStatus() + ")");
        }
        requireNoActivePayment(orderId);

        Long amount = order.getFinalAmount().getAmount();

        // 1) PENDING 결제 저장 — cardNo는 PaymentModel 내부에서 마스킹되어 영속된다.
        PaymentModel payment = paymentRepository.save(
                new PaymentModel(orderId, userId, cardType, cardNo, amount));

        // 2) PG 요청 (DB 트랜잭션 밖, 재시도/서킷 적용) — PG로는 원본 카드번호를 보낸다.
        //    재시도 소진/서킷 OPEN이면 어댑터 폴백이 CoreException(503)을 던진다. 이때 방금 만든 PENDING 결제를
        //    FAILED로 정리해 거래키 없는 고아 PENDING이 남지 않게 한다(없으면 멱등 가드에 걸려 재결제 불가).
        //    주문은 PENDING 그대로 둬 사용자가 다시 결제할 수 있게 한다.
        PgTransaction tx;
        try {
            tx = pgClient.requestPayment(
                    new PgPaymentRequest(orderId, userId, cardType, cardNo, amount, CALLBACK_URL));
        } catch (RuntimeException e) {
            payment.markFailed("PG 요청 실패: " + e.getMessage());
            paymentRepository.save(payment);
            throw e;
        }

        // 3) 발급받은 거래키 저장. 최종 상태(SUCCESS/FAILED)는 콜백/Reconcile에서 확정.
        payment.assignTransactionKey(tx.transactionKey());
        PaymentModel saved = paymentRepository.save(payment);

        return PaymentInfo.from(saved);
    }

    /**
     * PG 콜백 수신 처리 (03 §3.4). pg-simulator가 비동기 처리(1~5초) 후 callbackUrl로 통지하는
     * {@code TransactionInfo}를 받아 결제와 주문을 최종 확정한다. 확정 자체는 콜백·Reconcile이 공유하는
     * {@link PaymentConfirmer#confirm}에 위임한다(비관락 + 멱등 + 주문 cascade를 한 트랜잭션으로).
     */
    public PaymentInfo handleCallback(String transactionKey, PaymentStatus resultStatus, String reason) {
        return PaymentInfo.from(paymentConfirmer.confirm(transactionKey, resultStatus, reason));
    }

    /**
     * PENDING 결제 정리(reconcile) (03 §3.5). 콜백 유실로 PENDING에 남은 결제를 PG 진실원천
     * ({@code findTransactionsByOrder})으로 재확인해 최종 확정한다. 콜백이 영구 유실돼도 결제·주문이
     * 끝내 확정되도록 하는 안전망이다.
     * <p>
     * 각 결제 확정은 {@link PaymentConfirmer#confirm}의 독립 트랜잭션으로 처리되며(콜백과 동일 경로),
     * 한 건 실패가 배치 전체를 막지 않는다. PG 조회/확정은 DB 트랜잭션 밖에서 이뤄진다.
     * 트리거는 운영/배치(AdminPaymentV1Controller) — 별도 스케줄러는 두지 않는다.
     */
    public PaymentReconcileResult reconcilePending(int page, int size) {
        List<PaymentModel> pendings = paymentRepository.findByStatus(PaymentStatus.PENDING, page, size);
        int paid = 0, failed = 0, stillPending = 0, skipped = 0;
        for (PaymentModel payment : pendings) {
            String transactionKey = payment.getTransactionKey();
            // 거래키 없는 PENDING = PG 요청조차 못 한 고아(pay()가 이미 FAILED로 정리). 방어적 skip.
            if (transactionKey == null) {
                skipped++;
                continue;
            }
            // PG 진실원천에서 이 거래의 최종 상태를 확인한다(DB tx 밖).
            PgTransaction tx = pgClient.findTransactionsByOrder(payment.getOrderId()).stream()
                    .filter(t -> transactionKey.equals(t.transactionKey()))
                    .findFirst()
                    .orElse(null);
            if (tx == null || tx.isPending()) {
                stillPending++;   // PG도 미확정 → 다음 회차로 미룸
                continue;
            }
            try {
                PaymentModel confirmed = paymentConfirmer.confirm(transactionKey, tx.status(), tx.reason());
                switch (confirmed.getStatus()) {
                    case SUCCESS -> paid++;
                    case FAILED -> failed++;
                    default -> stillPending++;
                }
            } catch (CoreException e) {
                // 조회~확정 사이 다른 경로(콜백/동시 reconcile)가 먼저 확정 등 → 건너뜀(멱등)
                skipped++;
            }
        }
        return new PaymentReconcileResult(pendings.size(), paid, failed, stillPending, skipped);
    }

    /** (orderId)에 진행 중(PENDING) 또는 이미 성공(SUCCESS)한 결제가 있으면 중복 결제 차단. */
    private void requireNoActivePayment(Long orderId) {
        List<PaymentModel> existing = paymentRepository.findByOrderId(orderId);
        boolean hasActive = existing.stream()
                .anyMatch(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.SUCCESS);
        if (hasActive) {
            throw new CoreException(ErrorType.CONFLICT, "[orderId = " + orderId + "] 이미 진행 중이거나 완료된 결제가 있습니다.");
        }
    }
}
