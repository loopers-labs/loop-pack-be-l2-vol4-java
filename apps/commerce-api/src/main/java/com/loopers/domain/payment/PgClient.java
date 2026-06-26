package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

/**
 * 외부 PG 시스템과의 연동 경계(port). 도메인은 이 인터페이스에만 의존하고,
 * 실제 호출 수단(Feign/RestTemplate)·타임아웃·예외 변환은 인프라가 책임진다.
 */
public interface PgClient {

    /**
     * 결제 요청을 접수한다. 호출 실패 시 {@link PgClientException} 을 던진다.
     */
    PgPaymentResult requestPayment(PgPaymentCommand command);

    /**
     * transactionKey 로 결제 상세를 조회한다. 존재하지 않으면 {@link Optional#empty()}.
     */
    Optional<PgTransactionDetail> getTransaction(String userId, String transactionKey);

    /**
     * orderId 에 엮인 결제 목록을 조회한다. 없으면 빈 리스트.
     */
    List<PgTransactionDetail> findTransactionsByOrderId(String userId, String orderId);
}
