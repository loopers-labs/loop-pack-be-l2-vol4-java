package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OrderLines {

    private final Map<Long, Integer> quantitiesByProductId;

    private OrderLines(Map<Long, Integer> quantitiesByProductId) {
        this.quantitiesByProductId = quantitiesByProductId;
    }

    public static OrderLines from(List<OrderCommand.OrderLine> lines) {
        if (CollectionUtils.isEmpty(lines)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderCommand.OrderLine line : lines) {
            merged.merge(line.productId(), line.quantity(), Integer::sum);
        }
        return new OrderLines(Collections.unmodifiableMap(merged));
    }

    public Set<Long> productIds() {
        return quantitiesByProductId.keySet();
    }

    public int quantityOf(Long productId) {
        Integer qty = quantitiesByProductId.get(productId);
        if (qty == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "주문 항목에 없는 상품: " + productId);
        }
        return qty;
    }
}
