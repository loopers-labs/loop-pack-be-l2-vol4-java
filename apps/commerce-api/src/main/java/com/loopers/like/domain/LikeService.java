package com.loopers.like.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LikeService {

    public LikeModel getOrThrow(Optional<LikeModel> like) {
        return like.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요가 존재하지 않습니다."));
    }

    public LikeModel cancelLike(Optional<LikeModel> existing) {
        return existing.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요가 존재하지 않습니다."));
    }

    public LikeModel createLike(Optional<LikeModel> existing, Long userId, Long productId) {
        if (existing.isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
        return new LikeModel(userId, productId);
    }
}
