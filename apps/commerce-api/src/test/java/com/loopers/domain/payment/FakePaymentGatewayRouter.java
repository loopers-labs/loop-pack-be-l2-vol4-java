package com.loopers.domain.payment;

import java.util.List;

/**
 * 단위 테스트용 FakePaymentGatewayRouter.
 * - select() 는 항상 주입된 gateway 반환 (CircuitBreaker 상태 체크 우회).
 * - selectFails 가 true 면 라우팅 실패 (모든 PG 막힘) 시나리오 흉내.
 */
public class FakePaymentGatewayRouter extends PaymentGatewayRouter {

    private final PaymentGateway gateway;
    private boolean selectFails = false;

    public FakePaymentGatewayRouter(PaymentGateway gateway) {
        super(List.of(gateway), null);
        this.gateway = gateway;
    }

    public void simulateAllGatewaysDown() {
        this.selectFails = true;
    }

    @Override
    public PaymentGateway select(Payment payment) {
        if (selectFails) {
            throw new PgPermanentException("사용 가능한 PG 없음 (테스트 시뮬레이션)");
        }
        return gateway;
    }

    @Override
    public PaymentGateway gatewayFor(PgProvider provider) {
        return gateway;
    }
}
