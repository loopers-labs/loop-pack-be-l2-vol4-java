package com.loopers.dlq.domain;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 격리된(처리 불가) 메시지의 운영 레코드. .DLT 토픽을 적재해 담당자가 조회/재처리/폐기한다.
 * 실패 건만 들어와 저·바운드이고, NEW→RETRIED/DISCARDED 라이프사이클을 가진다(멱등 dedup 테이블과 성격이 다름).
 */
@Entity
@Table(name = "dlq_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DlqMessage extends BaseEntity {

    @Column(name = "original_topic", nullable = false)
    private String originalTopic;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "message_key")
    private String messageKey;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "exception_class")
    private String exceptionClass;

    @Column(name = "exception_message", columnDefinition = "TEXT")
    private String exceptionMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DlqMessageStatus status;

    private DlqMessage(String originalTopic, Integer originalPartition, Long originalOffset,
                       String messageKey, String payload, String exceptionClass, String exceptionMessage) {
        this.originalTopic = originalTopic;
        this.originalPartition = originalPartition;
        this.originalOffset = originalOffset;
        this.messageKey = messageKey;
        this.payload = payload;
        this.exceptionClass = exceptionClass;
        this.exceptionMessage = exceptionMessage;
        this.status = DlqMessageStatus.NEW;
    }

    public static DlqMessage of(String originalTopic, Integer originalPartition, Long originalOffset,
                                String messageKey, String payload, String exceptionClass, String exceptionMessage) {
        return new DlqMessage(originalTopic, originalPartition, originalOffset,
                messageKey, payload, exceptionClass, exceptionMessage);
    }

    public void markRetried() {
        this.status = DlqMessageStatus.RETRIED;
    }

    public void markDiscarded() {
        this.status = DlqMessageStatus.DISCARDED;
    }
}
