package com.loopers.application.order;

import java.util.List;

public class OrderCommand {

    public record Place(List<Line> items, Long couponId) {
        public Place(List<Line> items) {
            this(items, null);
        }
    }

    public record Line(Long productId, Integer quantity) {
    }
}
