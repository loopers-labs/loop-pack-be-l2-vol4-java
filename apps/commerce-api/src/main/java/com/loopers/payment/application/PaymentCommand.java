package com.loopers.payment.application;

import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentStatus;

public class PaymentCommand {

    public record Pay(Long userId, String orderNumber, CardType cardType, String cardNo) {
    }

    /**
     * 콜백·정합성 보정의 공유 입력. transactionKey 로 결제를 찾고, orderNumber·amount 로 진위를 검증한다.
     */
    public record Confirm(String transactionKey, String orderNumber, long amount, PaymentStatus status, String reason) {
    }
}
