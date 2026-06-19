package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeOutboxModel;
import com.loopers.domain.like.LikeOutboxRepository;
import com.loopers.domain.like.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeOutboxRepositoryImpl implements LikeOutboxRepository {

    private final LikeOutboxJpaRepository likeOutboxJpaRepository;

    @Override
    public LikeOutboxModel save(LikeOutboxModel outbox) {
        return likeOutboxJpaRepository.save(outbox);
    }

    @Override
    public List<LikeOutboxModel> findAllByStatusOrderByIdAsc(OutboxStatus status) {
        return likeOutboxJpaRepository.findAllByStatusOrderByIdAsc(status);
    }

    @Override
    public boolean markDoneIfPending(Long id) {
        return likeOutboxJpaRepository.markDoneIfPending(id, OutboxStatus.DONE, OutboxStatus.PENDING) > 0;
    }
}
