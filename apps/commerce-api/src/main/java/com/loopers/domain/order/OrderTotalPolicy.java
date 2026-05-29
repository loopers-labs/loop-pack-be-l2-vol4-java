package com.loopers.domain.order;

import com.loopers.domain.order.vo.Money;

import java.util.List;

public interface OrderTotalPolicy {
    Money calculate(List<OrderItemModel> items);
}
