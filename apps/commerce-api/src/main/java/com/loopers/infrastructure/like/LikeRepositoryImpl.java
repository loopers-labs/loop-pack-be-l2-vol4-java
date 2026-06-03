package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    /**
     * 순수 도메인 ↔ JPA 엔티티 경계.
     * - 신규(id == null): 매퍼로 엔티티를 만들어 INSERT.
     * - 기존(id != null): managed 엔티티를 로드해 가변 상태(likedAt)만 복사 → dirty checking으로 UPDATE.
     *   soft delete 상태(deletedAt)도 도메인 기준으로 delete()/restore() 동기화한다(둘 다 멱등).
     *   취소→재등록(reactivate)도 이 UPDATE 경로를 타므로 같은 행(같은 id/createdAt)이 유지된다.
     *   (BaseEntity의 id가 final이라 도메인을 그대로 새 엔티티로 만들면 INSERT로 오인되므로 이 경로가 필요하다.)
     */
    @Override
    public LikeModel save(LikeModel like) {
        if (like.getId() == null) {
            LikeEntity saved = likeJpaRepository.save(LikeEntityMapper.toEntity(like));
            return LikeEntityMapper.toDomain(saved);
        }
        LikeEntity entity = likeJpaRepository.findById(like.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + like.getId() + "] 좋아요를 찾을 수 없습니다."));
        entity.applyState(like.getLikedAt());
        if (like.isActive()) {
            entity.restore();
        } else {
            entity.delete();
        }
        return LikeEntityMapper.toDomain(likeJpaRepository.save(entity));
    }

    @Override
    public Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId).map(LikeEntityMapper::toDomain);
    }

    @Override
    public List<LikeModel> findActiveByProductId(Long productId) {
        return likeJpaRepository.findByProductIdAndDeletedAtIsNull(productId).stream()
                .map(LikeEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<LikeModel> findActiveByUserId(Long userId, int page, int size) {
        return likeJpaRepository.findByUserIdAndDeletedAtIsNullOrderByLikedAtDescIdDesc(userId, PageRequest.of(page, size)).stream()
                .map(LikeEntityMapper::toDomain)
                .toList();
    }

    @Override
    public Set<Long> findLikedProductIds(Long userId, Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(likeJpaRepository.findLikedProductIds(userId, productIds));
    }
}
