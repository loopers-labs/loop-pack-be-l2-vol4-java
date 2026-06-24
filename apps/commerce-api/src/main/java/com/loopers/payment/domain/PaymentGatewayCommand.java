package com.loopers.payment.domain;

public record PaymentGatewayCommand(Long userId, String orderNumber, long amount, CardType cardType, String cardNo) {
}
