package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeOutboxModel;
import com.loopers.domain.like.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LikeOutboxJpaRepository extends JpaRepository<LikeOutboxModel, Long> {

    List<LikeOutboxModel> findAllByStatusOrderByIdAsc(OutboxStatus status);

    // affected rows가 1이면 이 인스턴스가 처리 권한을 획득한 것이고 0이면 이미 다른 인스턴스가 처리 완료
    @Modifying
    @Query("UPDATE LikeOutboxModel l SET l.status = :done WHERE l.id = :id AND l.status = :pending")
    int markDoneIfPending(@Param("id") Long id, @Param("done") OutboxStatus done, @Param("pending") OutboxStatus pending);
}
