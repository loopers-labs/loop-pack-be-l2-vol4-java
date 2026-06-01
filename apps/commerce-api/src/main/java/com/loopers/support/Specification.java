package com.loopers.support;

public interface Specification<T> {
    boolean isSatisfiedBy(T candidate);
}
