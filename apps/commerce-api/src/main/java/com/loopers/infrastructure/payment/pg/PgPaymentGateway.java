package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PgPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PgPaymentGateway.class);

    private final PgClient pgClient;
    private final String callbackUrl;

    public PgPaymentGateway(PgClient pgClient, @Value("${pg.callback-url}") String callbackUrl) {
        this.pgClient = pgClient;
        this.callbackUrl = callbackUrl;
    }

    @Override
    public Result requestPayment(Command command) {
        PgV1Dto.PaymentRequest request = new PgV1Dto.PaymentRequest(
            String.valueOf(command.orderId()),
            command.cardType().name(),
            command.cardNo(),
            command.amount(),
            callbackUrl
        );
        try {
            PgV1Dto.PaymentResponse response = pgClient.requestPayment(String.valueOf(command.userId()), request);
            PgV1Dto.PaymentResponse.Data data = response.data();
            return new Result(data.transactionKey(), PaymentStatus.valueOf(data.status()), data.reason());
        } catch (FeignException e) {
            // 타임아웃(RetryableException)·5xx 모두 FeignException으로 수렴 → 도메인 의미 예외로 변환
            log.warn("PG 결제 요청 실패: httpStatus={}, message={}", e.status(), e.getMessage());
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청에 실패했습니다.");
        }
    }
}
