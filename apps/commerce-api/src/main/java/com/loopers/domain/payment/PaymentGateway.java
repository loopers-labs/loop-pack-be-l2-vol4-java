package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

/**
 * 외부 결제 시스템(PG)에 대한 도메인 포트.
 * 구현체(infrastructure)는 Feign + CircuitBreaker + Fallback 으로 이 계약을 만족시킨다.
 * 도메인은 이 인터페이스만 알 뿐, HTTP/Feign 의 존재를 모른다 (DIP).
 */
public interface PaymentGateway {

    /** 결제 요청(접수). 시스템 장애 시 구현체가 폴백/예외로 처리한다. */
    PgRequestResult requestPayment(PgPaymentCommand command);

    /** 트랜잭션 키로 결제 결과 조회 (콜백 검증·복구용) */
    Optional<PgTransactionResult> findByTransactionKey(String transactionKey);

    /** 주문 ID로 결제 결과 목록 조회 (txKey 를 못 받은 타임아웃 건 복구용) */
    List<PgTransactionResult> findByOrderId(Long orderId);
}
