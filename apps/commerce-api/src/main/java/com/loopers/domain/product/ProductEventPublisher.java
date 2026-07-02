package com.loopers.domain.product;

public interface ProductEventPublisher {
    void publish(ProductViewedEvent event);

    void publish(ProductListViewedEvent event);
}
