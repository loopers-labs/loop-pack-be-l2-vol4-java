package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;

/**
 * 결제 요청 입력 DTO (응용 계층). API request DTO 와 분리.
 */
public record PaymentCriteria(
        Long userId,
        Long orderId,
        CardType cardType,
        String cardNo
) {}
