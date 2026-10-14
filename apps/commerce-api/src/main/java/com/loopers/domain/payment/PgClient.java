package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

/**
 * PG 외부 시스템 추상화 — 도메인은 구체 구현(Feign/REST) 을 모른다.
 * <p>
 * 모든 호출은 다음 가정 위에서 동작한다:
 * - 지연될 수 있다 (타임아웃 발생 가능)
 * - 실패할 수 있다 (5xx, 회로 차단)
 * - 성공했지만 응답이 유실될 수 있다
 * 따라서 호출자는 응답 부재에 대한 자체 복구 경로를 가져야 한다.
 */
public interface PgClient {

    /**
     * PG 결제 요청 — transactionKey 발급받기.
     * @return PG 가 즉시 응답한 경우 transactionKey 포함. 실패/타임아웃은 PgRequestException 으로 신호.
     */
    PgRequestResponse request(PgRequestCommand command);

    /**
     * transactionKey 로 결제 상태 조회 (단건). 복구 폴링에서 사용.
     */
    Optional<PgTransactionView> getByTransactionKey(String transactionKey);

    /**
     * orderId 로 결제 정보들 조회. transactionKey 를 잃어버린 TIMEOUT_PENDING 결제건 복구에 사용.
     */
    List<PgTransactionView> findByOrderId(Long orderId);

    record PgRequestCommand(
        Long orderId,
        CardType cardType,
        String cardNo,
        Long amount,
        String callbackUrl,
        Long userId
    ) {}

    record PgRequestResponse(
        String transactionKey,
        PgTransactionStatus status
    ) {}

    record PgTransactionView(
        String transactionKey,
        Long orderId,
        PgTransactionStatus status,
        String reason
    ) {}

    enum PgTransactionStatus {
        PENDING, SUCCESS, FAILED;

        public boolean isTerminal() {
            return this == SUCCESS || this == FAILED;
        }
    }
}
