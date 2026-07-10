package com.loopers.domain.outbox;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(
    name = "outbox",
    indexes = {
        @Index(name = "idx_outbox_status_id", columnList = "status, id")
    }
)
public class OutboxModel extends BaseEntity {

    // 멱등성 키: 발행 시점에 생성된 UUID. 재전송해도 동일 값이며, 중복 처리 차단 기준이 된다.
    @Column(nullable = false, unique = true, length = 36)
    private String eventId;

    // 이벤트가 속한 애그리거트 종류 (예: "Like"). 브로커 라우팅/조회 시 사용.
    @Column(nullable = false)
    private String aggregateType;

    // 대상 애그리거트 식별자 (예: productId). 파티션 키로도 활용 가능.
    @Column(nullable = false)
    private String aggregateId;

    // 이벤트 종류. 릴레이가 이 값으로 핸들러를 디스패치한다.
    @Column(nullable = false)
    private String eventType;

    // 직렬화된 이벤트 본문. 핸들러가 이것으로 원본 이벤트를 복원한다.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    protected OutboxModel() {
    }

    public OutboxModel(String eventId, String aggregateType, String aggregateId, String eventType, String payload) {
        if (eventId == null || eventId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "eventId는 필수입니다.");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "aggregateType은 필수입니다.");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "aggregateId는 필수입니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "eventType은 필수입니다.");
        }
        if (payload == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "payload는 필수입니다.");
        }
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public static OutboxModel from(OutboxableEvent event, String payload) {
        return new OutboxModel(event.eventId(), event.aggregateType(), event.aggregateId(), event.eventType(), payload);
    }

    public void markDone() {
        this.status = OutboxStatus.DONE;
    }
}
