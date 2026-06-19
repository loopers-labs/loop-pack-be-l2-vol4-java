package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeOutboxService {

    private final LikeOutboxRepository likeOutboxRepository;

    public void record(Long productId, LikeEventType eventType) {
        likeOutboxRepository.save(new LikeOutboxModel(productId, eventType));
    }

    public List<LikeOutboxModel> findPending() {
        return likeOutboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING);
    }

    // 리스너의 트랜잭션에 합류하여 increaseLikeCount와 원자적으로 커밋된다.
    // true: 이 인스턴스가 처리 권한 획득, false: 이미 다른 인스턴스가 처리 완료
    @Transactional
    public boolean markDoneIfPending(Long id) {
        return likeOutboxRepository.markDoneIfPending(id);
    }
}
