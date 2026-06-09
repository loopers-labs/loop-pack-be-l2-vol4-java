package com.loopers.like.application;

import com.loopers.like.domain.LikeModel;
import com.loopers.like.domain.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class LikeService {

    private final LikeRepository likeRepository;

    /** 좋아요를 등록한다. 이미 등록되어 있으면 멱등하게 아무 동작도 하지 않는다. */
    public void like(Long memberId, Long productId) {
        if (!likeRepository.exists(memberId, productId)) {
            likeRepository.save(new LikeModel(memberId, productId));
        }
    }

    /** 좋아요를 취소한다. 등록되어 있지 않아도 멱등하게 처리한다. */
    public void unlike(Long memberId, Long productId) {
        likeRepository.delete(memberId, productId);
    }

    public long getLikeCount(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    public Map<Long, Long> getLikeCounts(Collection<Long> productIds) {
        return productIds.stream()
            .distinct()
            .collect(Collectors.toMap(Function.identity(), likeRepository::countByProductId));
    }

    public List<Long> getLikedProductIds(Long memberId) {
        return likeRepository.findByMemberId(memberId).stream()
            .map(LikeModel::getProductId)
            .toList();
    }
}
