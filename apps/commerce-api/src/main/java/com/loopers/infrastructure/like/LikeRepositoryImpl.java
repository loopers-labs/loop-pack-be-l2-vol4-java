package com.loopers.infrastructure.like;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public void save(Like like) {
        // INSERT IGNORE: 동시 요청에서 (user_id, product_id) PK 가 이미 들어가도 PK violation 을 일으키지 않고
        // 0행 영향으로 조용히 통과한다 — ERD 의 "PK 가 멱등성의 최종 방어선" 명세 그대로 구현.
        likeJpaRepository.insertIgnore(like.getUserId(), like.getProductId());
    }

    @Override
    public void delete(Long userId, Long productId) {
        likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    public boolean exists(Long userId, Long productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public long countByProductId(Long productId) {
        return likeJpaRepository.countByProductId(productId);
    }

    @Override
    public Map<Long, Long> countByProductIds(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return likeJpaRepository.findCountsByProductIds(productIds).stream()
                .collect(Collectors.toMap(
                        LikeJpaRepository.LikeCountRow::getProductId,
                        LikeJpaRepository.LikeCountRow::getCount
                ));
    }

    @Override
    public PageResult<Like> findAllByUserId(Long userId, int page, int size) {
        Page<Like> result = likeJpaRepository.findAllByUserId(
                userId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")))
        );
        return new PageResult<>(result.getContent(), page, size, result.hasNext(), result.getTotalElements());
    }
}
