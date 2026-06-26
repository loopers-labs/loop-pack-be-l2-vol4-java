package com.loopers.domain.payment;

/**
 * 결제 대행사(PG) 식별자. 멀티 PG 라우팅을 위한 식별값.
 *  - 6주차에서는 PG_SIMULATOR 하나만 운영하지만, Adapter / Router 구조는 N개 PG 대응 가능한 형태로 둔다.
 *  - 새 PG 추가 시 이 enum + PaymentGateway 구현체 + Router 매핑만 추가하면 된다.
 */
public enum PgProvider {
    PG_SIMULATOR,
    ;
}
