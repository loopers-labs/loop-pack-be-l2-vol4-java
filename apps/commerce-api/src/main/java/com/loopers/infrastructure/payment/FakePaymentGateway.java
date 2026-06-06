package com.loopers.infrastructure.payment;

import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentResult;
import com.loopers.domain.payment.PgStatus;
import org.springframework.stereotype.Component;

/**
 * 결제 게이트웨이 Fake 구현체. 기본은 SUCCESS를 반환하며,
 * 테스트가 결제 결과(실패/타임아웃)를 결정적으로 재현할 수 있도록 forcedStatus 시드를 제공한다.
 */
@Component
public class FakePaymentGateway implements PaymentGateway {

    private PgStatus forcedStatus = PgStatus.SUCCESS;
    private PgStatus forcedInquiryStatus = PgStatus.SUCCESS;

    /** 테스트 시드 — 다음 결제 결과를 강제한다. */
    public void setForcedStatus(PgStatus forcedStatus) {
        this.forcedStatus = forcedStatus;
    }

    /** 테스트 시드 — reconcile 시 inquire()가 반환할 사후 결과를 강제한다. */
    public void setForcedInquiryStatus(PgStatus forcedInquiryStatus) {
        this.forcedInquiryStatus = forcedInquiryStatus;
    }

    public void reset() {
        this.forcedStatus = PgStatus.SUCCESS;
        this.forcedInquiryStatus = PgStatus.SUCCESS;
    }

    @Override
    public PaymentResult pay(Long orderId, Long amount, PaymentMethod method) {
        return switch (forcedStatus) {
            case SUCCESS -> PaymentResult.success("FAKE-TX-" + orderId);
            case FAILED -> PaymentResult.failed("결제가 거절되었습니다.(Fake)");
            case TIMEOUT -> PaymentResult.timeout();
        };
    }

    @Override
    public PaymentResult inquire(Long orderId) {
        return switch (forcedInquiryStatus) {
            case SUCCESS -> PaymentResult.success("FAKE-TX-" + orderId);
            case FAILED -> PaymentResult.failed("결제가 거절되었습니다.(Fake)");
            case TIMEOUT -> PaymentResult.timeout();
        };
    }
}
