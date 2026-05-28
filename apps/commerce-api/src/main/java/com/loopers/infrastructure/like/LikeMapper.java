package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import org.springframework.stereotype.Component;

@Component
public class LikeMapper {

    public Like toDomain(LikeJpaEntity entity) {
        return Like.restore(entity.getUserId(), entity.getProductId());
    }

    public LikeJpaEntity toJpaEntity(Like domain) {
        return LikeJpaEntity.of(domain.getUserId(), domain.getProductId());
    }
}
