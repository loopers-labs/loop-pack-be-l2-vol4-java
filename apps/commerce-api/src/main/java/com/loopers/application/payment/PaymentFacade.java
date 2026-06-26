package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private static final Logger log = LoggerFactory.getLogger(PaymentFacade.class);

    private final UserService userService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    /**
     * 결제를 요청한다. 트랜잭션 경계와 외부 호출을 분리한다:
     * (tx) 결제건 생성 → (tx 밖) PG 호출 → (tx) transactionKey 연결.
     * PG HTTP 호출 동안 DB 커넥션을 점유하지 않아 풀 고갈/장애 전파를 막는다.
     */
    public PaymentInfo requestPayment(String loginId, String loginPw, Long orderId, CardType cardType, String cardNo) {
        UserModel user = userService.getUser(loginId, loginPw);
        OrderModel order = orderService.getOrder(orderId);

        if (!order.getUserId().equals(user.getId())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 주문만 결제할 수 있습니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "결제 가능한 주문 상태가 아닙니다.");
        }

        // 결제건 생성
        PaymentModel payment = paymentService.create(user.getId(), orderId, cardType, cardNo, order.getFinalPrice());

        // PG 결과 요청
        PaymentGateway.Result result = paymentGateway.requestPayment(
            new PaymentGateway.Command(user.getId(), orderId, cardType, cardNo, order.getFinalPrice())
        );

        // transactionKey 연결
        PaymentModel linked = paymentService.linkTransactionKey(payment.getId(), result.transactionKey());
        return PaymentInfo.from(linked);
    }

    /**
     * PG 콜백을 처리한다. 콜백 본문의 status를 신뢰하지 않고 PG에 재조회해 검증한다(위조 콜백 차단).
     * 콜백은 "결과가 나왔다"는 트리거로만 쓰고, 진실은 reconcile의 PG 조회로 확정한다.
     */
    public void handleCallback(String transactionKey) {
        reconcile(transactionKey);
    }

    /**
     * PG에 transactionKey를 재조회해 실제 상태로 정정한다. (콜백 검증·정합성 복구의 공통 경로)
     * GET은 멱등, applyResult/confirm도 멱등이라 여러 번 호출해도 안전하다.
     * PG가 아직 PENDING이면(처리 미완) 아무것도 하지 않는다.
     */
    public void reconcile(String transactionKey) {
        PaymentModel payment = paymentService.getByTransactionKey(transactionKey);
        PaymentGateway.Result real = paymentGateway.getTransaction(payment.getUserId(), transactionKey);

        if (real.status() == PaymentStatus.PENDING) {
            return;
        }
        paymentService.applyResult(transactionKey, real.status(), real.reason());
        if (real.status() == PaymentStatus.SUCCESS) {
            orderService.confirm(payment.getOrderId());
        }
    }

    /**
     * 콜백 누락 대비 — PENDING(키 보유) 결제건들을 PG 조회로 일괄 정정한다. (스케줄러/수동 복구가 호출)
     * 한 건 실패가 전체를 막지 않도록 건별로 격리한다.
     */
    public void reconcileAll() {
        paymentService.findRecoverable().forEach(payment -> {
            try {
                reconcile(payment.getTransactionKey());
            } catch (Exception e) {
                log.warn("결제 정합성 복구 실패: transactionKey={}", payment.getTransactionKey(), e);
            }
        });
    }
}
