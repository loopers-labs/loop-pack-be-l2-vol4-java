package com.loopers.order.application;

public record OrderInfo(String orderNumber, boolean payable, long finalAmount) {
}
