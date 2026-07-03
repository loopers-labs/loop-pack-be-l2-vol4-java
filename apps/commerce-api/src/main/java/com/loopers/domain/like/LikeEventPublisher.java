package com.loopers.domain.like;

public interface LikeEventPublisher {
    void publish(LikedEvent event);

    void publish(UnlikedEvent event);

    void publish(ProductLikedEvent event);

    void publish(ProductUnlikedEvent event);
}
