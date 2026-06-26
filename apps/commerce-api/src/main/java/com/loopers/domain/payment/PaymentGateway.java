package com.loopers.domain.payment;

/**
 * 외부 PG 와의 연동을 추상화한 Port. 멀티 PG 대응을 위해 인터페이스로 분리한다.
 *
 *  - 구현체(Adapter) 는 infrastructure 레이어에 위치 (RestTemplate / Feign / SDK 등 선택).
 *  - {@link PaymentGatewayRouter} 가 PG 헬스 상태(CircuitBreaker) 를 보고 적절한 구현체를 선택한다.
 *
 * 결과 처리 약속:
 *  - 정상 응답 → {@link PgResponse} 반환
 *  - 명백한 영구 에러(4xx)         → {@link PgPermanentException}
 *  - 결과 확정 불가(5xx / timeout) → {@link PgUnknownException}
 */
public interface PaymentGateway {

    /** 이 어댑터가 담당하는 PG 식별자. 라우팅에 사용. */
    PgProvider provider();

    /**
     * 이 어댑터의 결제 요청에 적용된 Resilience4j CircuitBreaker 인스턴스 이름.
     * 라우터가 CB Open 상태를 확인해 해당 PG 를 후보에서 제외하기 위함.
     */
    String cbName();

    /**
     * 결제 요청. 성공 시 PG 가 transactionKey 를 발급하고 PENDING 상태를 응답한다 (비동기 결제).
     * 실제 결제 결과는 콜백(또는 폴링) 으로 통보된다.
     */
    PgResponse request(PgRequest request);

    /**
     * 결제 상태 조회. 폴링(reconciler) 에서 사용. transactionKey 로 PG 의 현재 상태를 확인한다.
     * userId 는 PG 의 본인 거래만 조회 가능한 인증 요건 때문에 함께 전달한다.
     * 멱등하므로 retry 자유롭게 가능.
     */
    PgResponse getStatus(String transactionKey, Long userId);
}
