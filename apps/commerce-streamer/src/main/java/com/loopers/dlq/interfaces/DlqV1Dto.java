package com.loopers.dlq.interfaces;

import com.loopers.dlq.domain.DlqMessage;

import java.time.ZonedDateTime;

public class DlqV1Dto {

    /** 목록: 어떤 토픽에서 무슨 에러로 격리됐는지 한눈에. */
    public record Summary(
            Long id,
            String originalTopic,
            String exceptionClass,
            String exceptionMessage,
            String status,
            ZonedDateTime createdAt
    ) {
        public static Summary from(DlqMessage m) {
            return new Summary(m.getId(), m.getOriginalTopic(), m.getExceptionClass(),
                    m.getExceptionMessage(), m.getStatus().name(), m.getCreatedAt());
        }
    }

    /** 상세: payload·offset 까지 — 원인 분석용. */
    public record Detail(
            Long id,
            String originalTopic,
            Integer originalPartition,
            Long originalOffset,
            String messageKey,
            String payload,
            String exceptionClass,
            String exceptionMessage,
            String status,
            ZonedDateTime createdAt
    ) {
        public static Detail from(DlqMessage m) {
            return new Detail(m.getId(), m.getOriginalTopic(), m.getOriginalPartition(), m.getOriginalOffset(),
                    m.getMessageKey(), m.getPayload(), m.getExceptionClass(), m.getExceptionMessage(),
                    m.getStatus().name(), m.getCreatedAt());
        }
    }
}
