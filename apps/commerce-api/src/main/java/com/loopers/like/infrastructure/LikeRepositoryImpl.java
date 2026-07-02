package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    /**
     * 좋아요를 저장한다. 동시 등록의 정합성은 {@code product_like} 의 유니크 제약(member_id, product_id)이 보장한다.
     *
     * <p>동일 회원이 동시에 중복 등록을 시도하면 한 건만 저장되고 나머지는 제약 위반으로 실패한다(좋아요 수는 1 로 유지). 서로 다른 회원의 동시
     * 등록은 충돌 없이 각각 저장된다.
     */
    @Override
    public Like save(Like like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public boolean exists(Long memberId, Long productId) {
        return likeJpaRepository.existsByMemberIdAndProductId(memberId, productId);
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
    public List<Like> findByMemberId(Long memberId) {
        return likeJpaRepository.findByMemberId(memberId);
    }
}
