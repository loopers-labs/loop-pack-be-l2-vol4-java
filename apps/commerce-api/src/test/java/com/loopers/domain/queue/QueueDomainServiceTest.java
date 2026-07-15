package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueueDomainServiceTest {

    private final QueueDomainService queueDomainService = new QueueDomainService();

    @DisplayName("예상 대기 시간을 계산할 때,")
    @Test
    void estimateWaitSeconds_returnsRankDividedBySafeTps() {
        // arrange
        long rank = 300L;

        // act
        long result = queueDomainService.estimateWaitSeconds(rank);

        // assert
        assertThat(result).isEqualTo(300L / QueueThroughputPolicy.SAFE_TPS);
    }

    @DisplayName("순번이 0이면, 예상 대기 시간은 0초다.")
    @Test
    void estimateWaitSeconds_returnsZero_whenRankIsZero() {
        // act
        long result = queueDomainService.estimateWaitSeconds(0L);

        // assert
        assertThat(result).isZero();
    }
}
