package com.loopers.like.application;

import com.loopers.like.domain.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class LikeReader {

    private final LikeRepository likeRepository;

    public long countActive(Long productId) {
        return likeRepository.countActiveByProductId(productId);
    }

    public Map<Long, Long> countActiveByProductIds(List<Long> productIds) {
        return likeRepository.countActiveByProductIds(productIds);
    }
}
