package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayCommand;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 단일 PG(Toss) 게이트웨이. PG 응답엔 provider 가 없으므로 호출한 PG(TOSS)를 직접 찍고,
 * callbackUrl 도 provider 를 아는 여기서 조립한다. 멀티 PG·failover·resilience 조합은 이후 단계에서
 * 이 구현을 RoutingPaymentGateway 로 확장하며 추가한다.
 */
@Slf4j
@Component
public class PgPaymentGateway implements PaymentGateway {

    private static final PgProvider PROVIDER = PgProvider.TOSS;
    private static final String CALLBACK_PATH = "/api/v1/payments/callback/toss";

    private final TossPgClient tossPgClient;
    private final String callbackUrl;

    public PgPaymentGateway(TossPgClient tossPgClient,
                            @Value("${payment.pg.callback-base-url}") String callbackBaseUrl) {
        this.tossPgClient = tossPgClient;
        this.callbackUrl = callbackBaseUrl + CALLBACK_PATH;
    }

    @Override
    public PaymentGatewayResult request(PaymentGatewayCommand command) {
        PgPaymentRequest body = new PgPaymentRequest(
                command.orderNumber(),
                command.cardType().name(),
                command.cardNo(),
                command.amount(),
                callbackUrl);

        PgTransactionResponse data = tossPgClient.request(String.valueOf(command.userId()), body).data();

        log.info("PG 결제 요청 수락 provider={} orderNumber={} transactionKey={} status={}",
                PROVIDER, command.orderNumber(), data.transactionKey(), data.status());
        return new PaymentGatewayResult(
                data.transactionKey(),
                PROVIDER,
                PaymentStatus.valueOf(data.status()),
                data.reason());
    }
}
