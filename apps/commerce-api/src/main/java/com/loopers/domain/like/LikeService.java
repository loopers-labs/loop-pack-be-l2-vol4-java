package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    public Optional<LikeModel> find(UUID userId, UUID productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId);
    }

    /**
     * 좋아요 — 멱등 삽입(INSERT IGNORE). 새로 삽입됐으면 true, 이미 있으면 false.
     * 동시 중복 요청도 유니크 충돌을 예외 없이 무시하므로 트랜잭션 오염(rollback-only)이 없다.
     * Facade에서 true일 때만 likeCount 증가.
     */
    public boolean like(UUID userId, UUID productId) {
        return likeRepository.insertIgnore(userId, productId) == 1;
    }

    /**
     * 좋아요 취소 — 멱등 삭제. 실제 삭제됐으면 true, 없으면 false.
     * bulk DELETE의 실제 영향 행 수로 판정 → 동시 중복 취소 시 한쪽만 true.
     * Facade에서 true일 때만 likeCount 차감.
     */
    public boolean unlike(UUID userId, UUID productId) {
        return likeRepository.deleteIfExists(userId, productId) == 1;
    }

    public Page<LikeModel> findAllByUserId(UUID userId, Pageable pageable) {
        return likeRepository.findAllByUserId(userId, pageable);
    }

    /** Product+Brand fetch join 페이징 — N+1 방지 */
    public Page<LikeModel> findAllByUserIdWithProduct(UUID userId, Pageable pageable) {
        return likeRepository.findAllByUserIdWithProduct(userId, pageable);
    }
}
