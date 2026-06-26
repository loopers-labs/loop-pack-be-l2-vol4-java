package com.loopers.domain.payment;

import java.util.function.Function;

/**
 * 단위 테스트용 FakePaymentGateway. 시나리오별로 behavior 를 주입해
 * 정상 응답 / PgPermanentException / PgUnknownException / CallNotPermitted 등을 흉내낸다.
 *
 * 예외 케이스는 behavior 안에서 throw 하면 된다. (예: req -> { throw new PgPermanentException("..."); })
 */
public class FakePaymentGateway implements PaymentGateway {

    private Function<PgRequest, PgResponse> requestBehavior =
        req -> new PgResponse("TX-" + req.orderId(), PgStatus.PENDING, null);

    private Function<String, PgResponse> getStatusBehavior =
        key -> new PgResponse(key, PgStatus.PENDING, null);

    public void setRequestBehavior(Function<PgRequest, PgResponse> behavior) {
        this.requestBehavior = behavior;
    }

    public void setGetStatusBehavior(Function<String, PgResponse> behavior) {
        this.getStatusBehavior = behavior;
    }

    @Override
    public PgProvider provider() {
        return PgProvider.PG_SIMULATOR;
    }

    @Override
    public String cbName() {
        return "pgPaymentRequest";
    }

    @Override
    public PgResponse request(PgRequest request) {
        return requestBehavior.apply(request);
    }

    @Override
    public PgResponse getStatus(String transactionKey, Long userId) {
        return getStatusBehavior.apply(transactionKey);
    }
}
