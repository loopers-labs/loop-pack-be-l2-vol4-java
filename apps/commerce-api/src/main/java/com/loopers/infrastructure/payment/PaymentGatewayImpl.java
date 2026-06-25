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

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentGatewayImpl implements PaymentGateway {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 100;
    private static final long MAX_BACKOFF_MILLIS = 300;

    private final PaymentGatewayFeignClient paymentGatewayFeignClient;
    private final PaymentGatewayProperties pgSimulatorProperties;

    @Override
    public PaymentGatewayResponse requestPayment(PaymentGatewayRequest command) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return doRequestPayment(command);
            } catch (RetryableException e) {
                log.warn("PG 결제 요청 응답을 받지 못했습니다({}번째 시도). orderNumber={}", attempt, command.orderNumber(), e);

                // 응답을 못 받았다고 PG가 거래를 안 만들었다는 뜻은 아니므로(읽기 타임아웃 등),
                // 무작정 재시도하지 않고 orderId로 먼저 조회해 실제 결과를 확인한다.
                Optional<PaymentGatewayResponse> existing = queryExistingTransaction(command);
                if (existing.isPresent()) {
                    log.info("재시도 전 조회에서 기존 거래를 확인했습니다. orderNumber={}, transactionKey={}",
                            command.orderNumber(), existing.get().transactionKey());
                    return existing.get();
                }

                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                sleep(backoffMillis(attempt));
            }
        }
        throw new PaymentGatewayException(PaymentGatewayException.FailureReason.RETRY_FAILED, "PG 결제 요청이 재시도 후에도 실패했습니다.");
    }

    private long backoffMillis(int attempt) {
        return Math.min(INITIAL_BACKOFF_MILLIS * (1L << (attempt - 1)), MAX_BACKOFF_MILLIS);
    }

    private Optional<PaymentGatewayResponse> findByOrderNumber(String userNumber, String orderNumber) {
        try {
            PaymentGatewayFeignClient.TransactionsResponse response =
                    paymentGatewayFeignClient.findTransactionsByOrderId(userNumber, orderNumber);
            if (response == null || response.data() == null || response.data().transactions().isEmpty()) {
                return Optional.empty();
            }
            PaymentGatewayFeignClient.TransactionData transaction = response.data().transactions().get(0);
            return Optional.of(new PaymentGatewayResponse(transaction.transactionKey(), PaymentStatus.valueOf(transaction.status())));
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        }
    }

    private PaymentGatewayResponse doRequestPayment(PaymentGatewayRequest command) {
        PaymentGatewayFeignClient.TransactionResponse response;
        try {
            response = paymentGatewayFeignClient.requestPayment(
                    command.userNumber(),
                    new PaymentGatewayFeignClient.TransactionRequest(
                            command.orderNumber(),
                            command.cardType().name(),
                            command.cardNo(),
                            command.amount().longValueExact(),
                            pgSimulatorProperties.callbackUrl()
                    )
            );
        } catch (DecodeException e) {
            log.error("PG 결제 요청 응답 디코딩에 실패했습니다. orderNumber={}", command.orderNumber(), e);
            throw new PaymentGatewayException(PaymentGatewayException.FailureReason.DECODE_FAILED, "PG 결제 요청 응답 디코딩에 실패했습니다.");
        } catch (RetryableException e) {
            // 응답을 못 받은 경우라 PG가 거래를 만들었는지 알 수 없다 — 그대로 호출부(requestPayment)로 올려서
            // FeignException으로 뭉뚱그려 잡히지 않게 하고, 조회 후 재시도 판단을 거치게 한다.
            throw e;
        } catch (FeignException e) {
            log.error("PG 결제 요청에 실패했습니다. orderNumber={}", command.orderNumber(), e);
            throw new PaymentGatewayException(PaymentGatewayException.FailureReason.UNKNOWN, "PG 결제 요청에 실패했습니다.");
        }

        if (response == null || response.data() == null) {
            log.error("PG 결제 요청 응답 바디가 비정상적입니다. orderNumber={}", command.orderNumber());
            throw new PaymentGatewayException(PaymentGatewayException.FailureReason.EMPTY_RESPONSE, "PG 결제 요청 응답 바디가 비정상적입니다.");
        }
        return new PaymentGatewayResponse(response.data().transactionKey(), PaymentStatus.valueOf(response.data().status()));
    }

    private Optional<PaymentGatewayResponse> queryExistingTransaction(PaymentGatewayRequest command) {
        try {
            return findByOrderNumber(command.userNumber(), command.orderNumber());
        } catch (RuntimeException e) {
            log.error("PG 거래 조회에 실패해 재시도를 중단합니다. orderNumber={}", command.orderNumber(), e);
            throw new PaymentGatewayException(
                    PaymentGatewayException.FailureReason.UNKNOWN, "PG 결제 상태를 확인할 수 없어 재시도를 중단했습니다."
            );
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException(PaymentGatewayException.FailureReason.UNKNOWN, "PG 결제 요청 재시도 대기 중 인터럽트가 발생했습니다.");
        }
    }
}
