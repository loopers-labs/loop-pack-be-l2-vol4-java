package com.loopers.application.payment;

import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.Order;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.service.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

    private final MemberService memberService;
    private final OrderRepository orderRepository;
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
}
