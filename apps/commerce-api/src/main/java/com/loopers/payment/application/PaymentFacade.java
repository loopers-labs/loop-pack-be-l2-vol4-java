package com.loopers.payment.application;

import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayCommand;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 결제 요청 오케스트레이션. 트랜잭션 경계를 가르는 책임이라 메서드 전체에 @Transactional 을 걸지 않는다 —
 * TX1(PENDING 생성)·TX2(거래키 확정)는 PaymentService 의 트랜잭션 메서드에 위임하고,
 * 그 사이의 PG 호출은 어떤 트랜잭션에도 들어가지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    public PaymentResult.Accepted pay(PaymentCommand.Pay command) {
        PaymentResult.Pending pending = paymentService.createPending(command.userId(), command.orderNumber());

        PaymentGatewayResult result = paymentGateway.request(new PaymentGatewayCommand(
                command.userId(), pending.orderNumber(), pending.amount(), command.cardType(), command.cardNo()));

        paymentService.assignTransaction(pending.paymentId(), result.transactionKey(), result.pgProvider());

        log.info("결제 접수 orderNumber={} paymentId={} transactionKey={} provider={}",
                pending.orderNumber(), pending.paymentId(), result.transactionKey(), result.pgProvider());
        return new PaymentResult.Accepted(pending.paymentId(), pending.orderNumber(), PaymentStatus.PENDING);
    }
}
