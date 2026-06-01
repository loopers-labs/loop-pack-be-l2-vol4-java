package com.loopers.domain.coupon.specification;

@FunctionalInterface
public interface Specification<T> {

    boolean isSatisfiedBy(T candidate);
}
