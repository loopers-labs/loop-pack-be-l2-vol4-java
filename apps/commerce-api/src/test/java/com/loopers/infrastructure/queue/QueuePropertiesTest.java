package com.loopers.infrastructure.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueuePropertiesTest {

    @DisplayName("throughputPerSecond=70, schedulerIntervalMs=100이면 배치 크기는 7이다.")
    @Test
    void calculatesBatchSize_fromThroughputAndSchedulerInterval() {
        // given
        QueueProperties queueProperties = new QueueProperties(100, 70, true);

        // when
        int batchSize = queueProperties.batchSize();

        // then
        assertThat(batchSize).isEqualTo(7);
    }
}
