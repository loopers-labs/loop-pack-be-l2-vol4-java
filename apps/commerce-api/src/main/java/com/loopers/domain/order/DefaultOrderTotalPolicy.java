package com.loopers.domain.order;

import com.loopers.domain.order.vo.Money;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultOrderTotalPolicy implements OrderTotalPolicy {

    @Override
    public Money calculate(List<OrderItemModel> items) {
        long total = items.stream()
                .mapToLong(item -> item.getProductPrice().getValue() * item.getQuantity().getValue())
                .sum();
        return new Money(total);
    }
}
