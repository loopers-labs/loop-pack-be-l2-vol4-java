package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public final class OrderLines {

    private final List<OrderLine> values;

    private OrderLines(List<OrderLine> values) {
        this.values = values;
    }

    public static OrderLines of(List<OrderLine> raw) {
        if (raw == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 라인 목록은 null 일 수 없습니다.");
        }

        LinkedHashMap<Long, OrderLine> merged = new LinkedHashMap<>();
        for (OrderLine line : raw) {
            merged.merge(line.productId(), line, OrderLines::sumQuantity);
        }
        return new OrderLines(new ArrayList<>(merged.values()));
    }

    private static OrderLine sumQuantity(OrderLine existing, OrderLine incoming) {
        return new OrderLine(
            existing.productId(),
            existing.quantity() + incoming.quantity(),
            existing.productName(),
            existing.productPrice(),
            existing.brandName()
        );
    }

    public List<OrderLine> values() {
        return Collections.unmodifiableList(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Long totalAmount() {
        long total = 0L;
        for (OrderLine line : values) {
            long subtotal = Math.multiplyExact(line.productPrice(), (long) line.quantity());
            total = Math.addExact(total, subtotal);
        }
        return total;
    }
}
