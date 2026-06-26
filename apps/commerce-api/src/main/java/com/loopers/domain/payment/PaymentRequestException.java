package com.loopers.domain.payment;

/**
 * PG 결제 <b>요청</b>(접수) 단계의 실패를 나타내는 포트 계약 예외.
 *
 * <p>외부 결제 연동의 본질: 요청이 끊겼을 때 그 실패는 두 종류로만 갈린다.
 * <ul>
 *   <li><b>in-doubt</b>: 요청이 PG 에 닿았을 수 있어 <b>청구 여부를 모른다</b>(read 타임아웃, 5xx 등).
 *       함부로 실패로 단정하면 실제 청구된 건을 잃으므로, 결제는 {@code PENDING} 으로 두고 콜백/정산으로 확정한다.</li>
 *   <li><b>not in-doubt</b>: 요청이 PG 에 닿지 못했거나(연결 거부·CB-open) 닿았어도 거절(4xx)이라
 *       <b>청구가 일어나지 않았음이 확실</b>하다. 이때 {@code PENDING} 으로 숨기면 콜백도 정산도 닿지 못하는
 *       좀비가 되므로 즉시 {@code FAILED} 로 확정해 재시도를 열어준다.</li>
 * </ul>
 *
 * <p>어떤 저수준 예외(타임아웃/CB-open/연결거부/4xx…)가 어느 갈래인지는 어댑터(infrastructure)가 판정하고,
 * 응용은 {@link #isInDoubt()} 한 비트만 보고 PENDING/FAILED 를 가른다. 즉 도메인 계약은 "모름이냐 아니냐"만
 * 알면 되고, PG 별 예외 분류 디테일은 어댑터에 가둔다.</p>
 */
public class PaymentRequestException extends RuntimeException {

    private final boolean inDoubt;

    private PaymentRequestException(boolean inDoubt, String message) {
        super(message);
        this.inDoubt = inDoubt;
    }

    /** 청구 여부 불명 → 결제를 PENDING 으로 유지하고 콜백/정산으로 확정한다. */
    public static PaymentRequestException inDoubt(String message) {
        return new PaymentRequestException(true, message);
    }

    /** 청구가 일어나지 않음이 확실 → 결제를 FAILED 로 확정한다(재시도 허용). */
    public static PaymentRequestException notInDoubt(String message) {
        return new PaymentRequestException(false, message);
    }

    public boolean isInDoubt() {
        return inDoubt;
    }
}
