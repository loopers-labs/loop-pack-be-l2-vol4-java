package com.loopers.application.order;

import java.util.List;

public class OrderCommand {

    public record Place(List<Line> items) {
    }

    public record Line(Long productId, Integer quantity) {
    }
}
