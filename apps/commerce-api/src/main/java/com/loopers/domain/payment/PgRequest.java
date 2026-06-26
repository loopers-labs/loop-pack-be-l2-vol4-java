package com.loopers.domain.payment;

import com.loopers.domain.shared.Money;

/**
 * PG 결제 요청 캡슐. 우리 내부 Payment 와는 분리된 외부 호출용 DTO.
 *
 *  - cardNo 는 원본 카드번호로, PG 호출 시점에만 메모리에 두고 즉시 폐기.
 *    Payment 엔티티에는 마지막 4자리만 영속화 (PCI-DSS).
 *  - callbackUrl 은 어댑터 책임 (PG 별로 endpoint 가 다를 수 있어서 yml 에서 어댑터가 직접 주입).
 */
public record PgRequest(
    Long orderId,
    Long userId,
    CardType cardType,
    String cardNo,
    Money amount
) {
}
