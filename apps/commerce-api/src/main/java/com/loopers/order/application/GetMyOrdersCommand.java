package com.loopers.order.application;

import java.time.LocalDate;

public record GetMyOrdersCommand(
    Long userId,
    int page,
    int size,
    LocalDate startAt,
    LocalDate endAt
) {
}
