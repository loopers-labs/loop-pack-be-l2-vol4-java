package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public boolean saveIfAbsent(Long memberId, Long productId) {
        return likeJpaRepository.insertIgnore(memberId, productId) == 1;
    }

    @Override
    public void delete(Long memberId, Long productId) {
        likeJpaRepository.findByMemberIdAndProductId(memberId, productId)
            .ifPresent(likeJpaRepository::delete);
    }

    @Override
    public long countByProductId(Long productId) {
        return likeJpaRepository.countByProductId(productId);
    }

    @Override
    public Map<Long, Long> countByProductIds(Collection<Long> productIds) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        likeJpaRepository.countByProductIds(productIds)
            .forEach(projection -> counts.put(projection.getProductId(), projection.getLikeCount()));
        return counts;
    }

    @Override
    public List<Like> findByMemberId(Long memberId) {
        return likeJpaRepository.findByMemberId(memberId);
    }
}
