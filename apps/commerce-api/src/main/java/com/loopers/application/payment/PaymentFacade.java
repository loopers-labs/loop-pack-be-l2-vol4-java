package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentGatewayClient;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentPolicy;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.enums.CardType;
import com.loopers.domain.payment.enums.PaymentStatus;
import com.loopers.domain.payment.enums.PgTransactionStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PaymentPolicy paymentPolicy;
    private final PaymentGatewayClient paymentGatewayClient;
    private final TransactionTemplate transactionTemplate;

    public PaymentInfo createPayment(String orderNumber, Long userId, CardType cardType, String cardNo) {
        OrderModel order = orderService.getByOrderNumberAndUserId(orderNumber, userId);
        paymentPolicy.validatePayable(order, ZonedDateTime.now());

        PaymentModel payment = paymentService.create(order.getId(), order.getTotalMoney());

        try {
            PaymentGatewayClient.Result result = paymentGatewayClient.request(
                    PaymentGatewayClient.Command.of(userId, order, cardType, cardNo)
            );
            // PENDING 커밋
            paymentService.startProcessing(payment.getId(), result.transactionKey());
        } catch (CallNotPermittedException e) {
            log.warn("PG 서킷 오픈 [paymentId={}]", payment.getId());
            paymentService.fail(payment.getId());
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "PG 일시 점검 중입니다. 잠시 후 다시 시도해주세요.");
        } catch (Exception e) {
            log.warn("PG 결제 요청 실패 [paymentId={}]: {}", payment.getId(), e.getMessage());
            paymentService.fail(payment.getId());
        }

        return PaymentInfo.from(paymentService.get(payment.getId()));
    }

    public PaymentInfo syncPayment(String transactionKey, Long userId) {
        PaymentModel payment = paymentService.getByTransactionKey(transactionKey);
        orderService.getByUser(payment.getOrderId(), userId);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return PaymentInfo.from(payment);
        }
        try {
            PaymentGatewayClient.QueryResult result = paymentGatewayClient.query(transactionKey, userId);
            PaymentModel updated = transactionTemplate.execute(status -> {
                PaymentModel applied = paymentService.applyCallback(transactionKey, result.status());
                if (applied.getStatus() == PaymentStatus.APPROVED) {
                    orderService.complete(applied.getOrderId());
                }
                return applied;
            });
            return PaymentInfo.from(updated);
        } catch (CallNotPermittedException e) {
            log.warn("PG 서킷 오픈 - 상태 조회 불가 [transactionKey={}]", transactionKey);
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "PG 일시 점검 중입니다. 잠시 후 다시 시도해주세요.");
        } catch (Exception e) {
            log.warn("PG 상태 조회 실패 [transactionKey={}]: {}", transactionKey, e.getMessage());
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 상태 조회에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @Transactional
    public void handleCallback(String transactionKey, PgTransactionStatus pgStatus) {
        PaymentModel payment = paymentService.applyCallback(transactionKey, pgStatus);
        if (payment.getStatus() == PaymentStatus.APPROVED) {
            orderService.complete(payment.getOrderId());
        }
    }

}
