package com.loopers.application.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueuePropertiesTest {

    @DisplayName("배출 속도·TTL 설정값이 0 이하면, 어떤 프로퍼티가 잘못됐는지 명시하며 기동 시점에 즉시 실패한다. (fail-fast)")
    @Test
    void failsFast_whenNonPositiveValue() {
        // act
        IllegalArgumentException batchSizeEx = assertThrows(IllegalArgumentException.class,
            () -> new QueueProperties.Admission(100, 0));
        IllegalArgumentException intervalEx = assertThrows(IllegalArgumentException.class,
            () -> new QueueProperties.Admission(-1, 14));
        IllegalArgumentException ttlEx = assertThrows(IllegalArgumentException.class,
            () -> new QueueProperties.Token(0));

        // assert — batch-size 0 은 ceilDiv ArithmeticException 으로 순번 조회 전원 500이 되는 값이라 기동 차단이 목적
        assertAll(
            () -> assertThat(batchSizeEx.getMessage()).contains("queue.admission.batch-size"),
            () -> assertThat(intervalEx.getMessage()).contains("queue.admission.interval-ms"),
            () -> assertThat(ttlEx.getMessage()).contains("queue.token.ttl-seconds")
        );
    }
}
