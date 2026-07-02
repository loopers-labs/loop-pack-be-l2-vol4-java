package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductEventPublisher;
import com.loopers.domain.product.ProductListViewedEvent;
import com.loopers.domain.product.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductCoreEventPublisher implements ProductEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(ProductViewedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publish(ProductListViewedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
