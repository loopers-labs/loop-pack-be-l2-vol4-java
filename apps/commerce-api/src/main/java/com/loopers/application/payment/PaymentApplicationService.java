package com.loopers.application.payment;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.Order;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentStatus;
import com.loopers.domain.payment.service.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

    private final MemberService memberService;
    private final OrderRepository orderRepository;
    private final OrderApplicationService orderApplicationService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    @Value("${pg.callback-url}")
    private String callbackUrl;

    public PaymentInfo requestPayment(String loginId, Long orderId, CardType cardType, String cardNo) {
        Member member = memberService.getMember(loginId);
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.getMemberId().equals(member.getId())) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 결제할 수 있습니다.");
        }

        // Tx1: 결제 생성 (멱등성 검증 포함)
        Payment payment = paymentService.initiate(
            orderId, member.getId(), cardType, cardNo, order.getTotalAmount());

        // Tx 밖: 외부 PG 호출
        PaymentGatewayResult result = paymentGateway.requestPayment(new PaymentGatewayCommand(
            String.valueOf(member.getId()),
            String.valueOf(order.getOrderCode()),
            cardType,
            cardNo,
            order.getTotalAmount(),
            callbackUrl
        ));

        // Tx2: PG 트랜잭션 키 반영 (fallback 시 키가 없을 수 있음 -> 폴링 복구가 처리)
        if (result.transactionKey() != null) {
            paymentService.assignTransactionKey(payment.getId(), result.transactionKey());
        }

        return new PaymentInfo(payment.getId(), result.status());
    }

    @Transactional
    public void confirmPayment(String transactionKey, PaymentStatus status, String reason) {
        Payment confirmed = paymentService.confirmResult(transactionKey, status, reason);
        // 결제가 FAILED 로 새로 확정되면 주문 취소 + 재고/쿠폰 보상
        if (confirmed != null && confirmed.getStatus() == PaymentStatus.FAILED) {
            orderApplicationService.compensateOrder(confirmed.getOrderId());
        }
    }

    /**
     * 콜백이 유실됐을 수 있는 PENDING 결제를, PG에 직접 조회해 상태를 확정한다.
     * 개별 결제 조회가 실패해도 나머지 결제 복구는 계속한다.
     */
    public void recoverPendingPayments() {
        List<Payment> pendings = paymentService.findRecoverablePayments();
        for (Payment payment : pendings) {
            try {
                PaymentGatewayResult result = paymentGateway.findTransaction(
                    String.valueOf(payment.getMemberId()), payment.getTransactionKey());
                if (result.status() != PaymentStatus.PENDING) {
                    confirmPayment(payment.getTransactionKey(), result.status(), result.reason());
                }
            } catch (Exception e) {
                log.warn("결제 복구 조회 실패. paymentId={}, cause={}", payment.getId(), e.toString());
            }
        }
    }

    /**
     * 요청이 fallback/타임아웃으로 트랜잭션 키를 받지 못한 PENDING 결제를,
     * 주문번호(orderCode)로 PG에 조회해 실제로 접수됐는지 확인하고 키/상태를 복구한다.
     */
    public void recoverUnconfirmedRequests() {
        List<Payment> targets = paymentService.findUnconfirmedRequests();
        for (Payment payment : targets) {
            try {
                Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
                if (order == null) {
                    continue;
                }
                List<PaymentGatewayResult> results = paymentGateway.findTransactionsByOrder(
                    String.valueOf(payment.getMemberId()), String.valueOf(order.getOrderCode()));
                if (results.isEmpty()) {
                    continue; // PG에 아직 없음 -> 다음 주기에 재확인
                }
                PaymentGatewayResult result = results.get(0);
                paymentService.assignTransactionKey(payment.getId(), result.transactionKey());
                if (result.status() != PaymentStatus.PENDING) {
                    confirmPayment(result.transactionKey(), result.status(), result.reason());
                }
            } catch (Exception e) {
                log.warn("미접수 결제 복구 실패. paymentId={}, cause={}", payment.getId(), e.toString());
            }
        }
    }
}
