package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeEventPublisher;
import com.loopers.domain.like.LikedEvent;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.like.UnlikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeCoreEventPublisher implements LikeEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(LikedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publish(UnlikedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publish(ProductLikedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publish(ProductUnlikedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
