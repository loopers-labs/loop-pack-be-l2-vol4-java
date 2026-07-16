package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueuePositionTest {

    @DisplayName("대기 중 유저의 예상 대기 시간은 순번 / 초당 처리량 이다.")
    @Test
    void waiting_estimatesWaitByPositionOverThroughput() {
        // act — 순번 350, 처리량 175 → 2초
        QueuePosition status = QueuePosition.waiting(350L, 175L);

        // assert
        assertThat(status.position()).isEqualTo(350L);
        assertThat(status.admitted()).isFalse();
        assertThat(status.token()).isNull();
        assertThat(status.estimatedWaitSeconds()).isEqualTo(2L);
    }

    @DisplayName("나눗셈이 딱 떨어지지 않으면 올림한다(순번 1 → 최소 1초).")
    @Test
    void waiting_roundsUp() {
        // act — 1 / 175 = 0.0057 → 올림 1
        QueuePosition status = QueuePosition.waiting(1L, 175L);

        // assert
        assertThat(status.estimatedWaitSeconds()).isEqualTo(1L);
    }

    @DisplayName("입장 토큰을 발급받은 유저는 순번 0·대기시간 0·토큰 포함 상태다.")
    @Test
    void admitted_hasTokenAndZeroPosition() {
        // act
        QueuePosition status = QueuePosition.admitted("token-abc");

        // assert
        assertThat(status.position()).isZero();
        assertThat(status.admitted()).isTrue();
        assertThat(status.token()).isEqualTo("token-abc");
        assertThat(status.estimatedWaitSeconds()).isZero();
    }
}