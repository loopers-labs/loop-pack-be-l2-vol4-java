package com.loopers.like.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LikeService {

    public LikeModel cancelLike(Optional<LikeModel> existing) {
        return existing.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요가 존재하지 않습니다."));
    }

    // 중복 검증은 LikeRegistrationPolicy 책임 → 여기서는 순수 생성만 담당
    public LikeModel createLike(Long userId, Long productId) {
        return new LikeModel(userId, productId);
    }
}
