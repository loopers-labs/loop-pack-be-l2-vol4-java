package com.loopers.infrastructure.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentGatewayImpl implements PaymentGateway {

    private static final String EXTERNAL_ORDER_ID_FORMAT = "%06d";

    private final PgSimulatorClient pgSimulatorClient;

    @Value("${pg-simulator.callback-url}")
    private String callbackUrl;

    @Override
    public String requestPayment(PaymentModel payment) {
        PgSimulatorDto.PaymentRequest request = new PgSimulatorDto.PaymentRequest(
            toExternalOrderId(payment.getOrderId()),
            payment.getCardType().name(),
            payment.getCardNo().value(),
            (long) payment.getAmount(),
            callbackUrl
        );

        try {
            return pgSimulatorClient.requestPayment(String.valueOf(payment.getUserId()), request)
                .data()
                .transactionKey();
        } catch (FeignException e) {
            log.warn("PG 결제 접수 호출 실패 (orderId={}): {}", payment.getOrderId(), e.getMessage());
            throw new CoreException(ErrorType.PAYMENT_GATEWAY_ERROR, "결제 시스템 연동에 실패했습니다.");
        }
    }

    private String toExternalOrderId(Long orderId) {
        return String.format(EXTERNAL_ORDER_ID_FORMAT, orderId);
    }
}
