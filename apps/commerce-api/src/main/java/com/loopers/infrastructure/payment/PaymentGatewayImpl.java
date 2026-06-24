package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.payment.PaymentGatewayRequest;
import com.loopers.domain.payment.PaymentGatewayResponse;
import com.loopers.domain.payment.PaymentStatus;
import feign.FeignException;
import feign.RetryableException;
import feign.codec.DecodeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentGatewayImpl implements PaymentGateway {

    private final PaymentGatewayFeignClient paymentGatewayFeignClient;
    private final PaymentGatewayProperties pgSimulatorProperties;

    @Override
    public PaymentGatewayResponse requestPayment(PaymentGatewayRequest command) {

        try {
            PaymentGatewayFeignClient.Response response = paymentGatewayFeignClient.requestPayment(
                    command.userNumber(),
                    new PaymentGatewayFeignClient.Request(
                            command.orderNumber(),
                            command.cardType().name(),
                            command.cardNo(),
                            command.amount().longValueExact(),
                            pgSimulatorProperties.callbackUrl()
                    )
            );
            if (response == null || response.data() == null) {
                log.error("PG 응답 바디가 비정상적입니다. orderNumber={}", command.orderNumber());
                throw new PaymentGatewayException(PaymentGatewayException.FailureReason.EMPTY_RESPONSE, "PG 결제 요청에 실패했습니다.");
            }
            return new PaymentGatewayResponse(
                    response.data().transactionKey(),
                    PaymentStatus.valueOf(response.data().status())
            );
        } catch (DecodeException e) {
            log.error("PG 응답 디코딩에 실패했습니다. orderNumber={}", command.orderNumber(), e);
            throw new PaymentGatewayException(PaymentGatewayException.FailureReason.DECODE_FAILED, "PG 결제 요청에 실패했습니다.");
        } catch (RetryableException e) {
            log.error("PG 결제 요청 재시도가 모두 실패했습니다. orderNumber={}", command.orderNumber(), e);
            throw new PaymentGatewayException(PaymentGatewayException.FailureReason.RETRY_FAILED, "PG 결제 요청이 재시도 후에도 실패했습니다.");
        } catch (FeignException e) {
            log.error("PG 결제 요청에 실패했습니다. orderNumber={}", command.orderNumber(), e);
            throw new PaymentGatewayException(PaymentGatewayException.FailureReason.UNKNOWN, "PG 결제 요청에 실패했습니다.");
        }
    }
}
