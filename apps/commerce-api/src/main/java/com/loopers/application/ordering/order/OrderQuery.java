package com.loopers.application.ordering.order;

import java.time.LocalDate;

public class OrderQuery {
    public record ListOrders(String userId, LocalDate startAt, LocalDate endAt) {}
}
