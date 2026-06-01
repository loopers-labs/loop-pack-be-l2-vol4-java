package com.loopers.order.domain;

import com.loopers.support.Specification;

public class OrderOwnerSpecification implements Specification<OrderModel> {

    private final Long userId;

    public OrderOwnerSpecification(Long userId) {
        this.userId = userId;
    }

    @Override
    public boolean isSatisfiedBy(OrderModel order) {
        return order.getUserId().equals(userId);
    }
}
