package com.loopers.application.order;

import com.loopers.domain.order.OrderCommand;

import java.time.LocalDate;
import java.util.List;

public final class OrderCriteria {

    private OrderCriteria() {
    }

    public record Place(Long userId, Long couponId, List<Line> lines) {
    }

    public record Line(Long productId, Integer quantity) {

        public OrderCommand.OrderLine toDomain() {
            return OrderCommand.OrderLine.of(productId, quantity);
        }
    }

    public record MySearch(Long userId, LocalDate from, LocalDate to, int page, int size) {

        public OrderCommand.MySearch toDomain() {
            return new OrderCommand.MySearch(userId, from, to, page, size);
        }
    }

    public record AdminSearch(int page, int size) {

        public OrderCommand.AdminSearch toDomain() {
            return new OrderCommand.AdminSearch(page, size);
        }
    }
}
