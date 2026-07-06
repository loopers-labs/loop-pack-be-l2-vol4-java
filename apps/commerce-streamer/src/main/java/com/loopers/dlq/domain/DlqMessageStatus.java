package com.loopers.dlq.domain;

public enum DlqMessageStatus {
    NEW,        // 적재됨, 담당자 확인 전
    RETRIED,    // 원본 토픽으로 재발행함
    DISCARDED   // 폐기 처리함
}
