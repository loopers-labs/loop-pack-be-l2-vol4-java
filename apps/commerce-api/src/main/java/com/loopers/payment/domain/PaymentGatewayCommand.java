package com.loopers.payment.domain;

public record PaymentGatewayCommand(String orderNumber, long amount, CardType cardType, String cardNo) {
}
