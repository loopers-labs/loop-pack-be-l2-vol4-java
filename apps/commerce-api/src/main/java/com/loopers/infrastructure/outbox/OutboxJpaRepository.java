package com.loopers.infrastructure.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxEntity, Long> {

    /**
     * 발행 대기(PENDING) 메시지를 생성 순서(id ASC)로 조회한다. TSID가 아닌 IDENTITY PK라도
     * 단조 증가하므로 id ASC = 기록 순서. 같은 파티션 키의 순서를 보존하려면 오래된 것부터 보낸다.
     */
    List<OutboxEntity> findByStatusOrderByIdAsc(OutboxStatus status, Pageable pageable);

    /**
     * 상태별 메시지 수. Micrometer 백로그 게이지({@code outbox.pending.count}/{@code outbox.failed.count})가
     * 스크레이프 시 호출한다. {@code (status, id)} 인덱스로 저렴하게 카운트된다.
     */
    long countByStatus(OutboxStatus status);
}
