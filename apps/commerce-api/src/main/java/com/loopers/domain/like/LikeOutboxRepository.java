package com.loopers.domain.like;

import java.util.List;

public interface LikeOutboxRepository {

    LikeOutboxModel save(LikeOutboxModel outbox);

    List<LikeOutboxModel> findAllByStatusOrderByIdAsc(OutboxStatus status);

    // 멱등성 보장: UPDATE WHERE status='PENDING' 의 원자적 실행으로 동시에 같은 레코드를 처리하려는 인스턴스 중 하나만 true를 반환받는다.
    boolean markDoneIfPending(Long id);
}
