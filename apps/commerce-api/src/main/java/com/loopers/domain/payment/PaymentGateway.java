package com.loopers.domain.payment;

import java.util.Optional;

/**
 * 외부 결제대행(PG) 연동 포트.
 *
 * <p>DIP: 도메인이 추상(포트)을 정의하고 infrastructure 가 구현(어댑터)한다.
 * 어댑터는 별도 빈으로 분리되어, 후속 단계에서 Timeout/CircuitBreaker 데코레이션을 프록시로 받을 수 있다.</p>
 */
public interface PaymentGateway {

    /**
     * PG 에 결제를 요청(접수)한다. 비동기 PG 이므로 응답은 transactionKey + 접수 상태(보통 PENDING)이며,
     * 최종 확정은 콜백/조회로 이뤄진다.
     */
    Result request(Command command);

    /**
     * transactionKey 로 PG 의 현재 결제 상태를 조회한다(콜백 이중화·정산의 source of truth).
     * 해당 키가 PG 에 없으면 {@link Optional#empty()}.
     */
    Optional<Result> find(String transactionKey, String userId);

    /**
     * @param cardNo 풀 PAN — PG 호출에만 쓰이는 transient 값
     */
    record Command(
            String userId,
            String orderId,
            CardType cardType,
            String cardNo,
            long amount,
            String callbackUrl
    ) {
    }

    record Result(
            String transactionKey,
            PaymentStatus status,
            String reason
    ) {
    }
}
