package com.loopers.domain.product;

public interface ProductLikeCountRepository {

    void increment(Long productId);

    void decrement(Long productId);
}