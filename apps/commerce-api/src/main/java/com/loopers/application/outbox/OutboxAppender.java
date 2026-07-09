package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.outbox.OutboxEntity;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시스템 간 전파가 필요한 이벤트를 outbox 테이블에 적재한다.
 *
 * <p><b>핵심 불변식</b>: 이 INSERT는 도메인 상태 변경과 같은 트랜잭션 안에서 일어나야 한다
 * ({@link Propagation#MANDATORY}로 호출자 트랜잭션을 강제). 도메인 변경과 outbox 기록이 원자적으로
 * 커밋/롤백되어야 "메시지 없는 도메인 변경"이나 "고아 이벤트"가 생기지 않는다.
 *
 * <p>실제 Kafka 전송은 이 트랜잭션 밖에서 이뤄진다. 하이브리드 발행: 커밋 직후
 * {@link OutboxImmediatePublisher}가 저지연으로 즉시 발행하고, 실패분은 {@link OutboxRelay} 폴링이 재발행한다.
 * 즉시 발행 빈은 test 프로파일/비활성화 시 존재하지 않으므로 {@link ObjectProvider}로 선택적으로 예약한다.
 */
@Component
@RequiredArgsConstructor
public class OutboxAppender {

    private final OutboxJpaRepository outboxJpaRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<OutboxImmediatePublisher> immediatePublisher;

    /**
     * @param aggregateType 집계 루트 종류(like / product / order / coupon)
     * @param aggregateId   집계 루트 식별자
     * @param eventType     이벤트 종류(envelope의 eventType)
     * @param topic         발행 대상 토픽
     * @param partitionKey  파티션 키(같은 키는 순서 보장). null이면 라운드로빈.
     * @param payload       이벤트 본문 객체(JSON 직렬화되어 저장)
     * @param version       최신성 비교 기준
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEntity append(String aggregateType, Long aggregateId, String eventType, String topic,
                               String partitionKey, Object payload, long version) {
        String payloadJson = serialize(payload);
        OutboxEntity entity = new OutboxEntity(aggregateType, aggregateId, eventType, topic, partitionKey, payloadJson, version);
        OutboxEntity saved = outboxJpaRepository.save(entity);
        // 커밋 직후 즉시 발행 예약(저지연). 빈이 없으면(test·비활성) no-op → 릴레이 폴링만으로 발행.
        immediatePublisher.ifAvailable(publisher -> publisher.scheduleAfterCommit(saved.getId()));
        return saved;
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "이벤트 payload 직렬화 실패: " + e.getMessage());
        }
    }
}
