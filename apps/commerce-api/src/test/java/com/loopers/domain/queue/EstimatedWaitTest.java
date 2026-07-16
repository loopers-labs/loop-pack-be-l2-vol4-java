package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EstimatedWaitTest {

    @DisplayName("순번이 0이면 예상 대기는 '곧 주문 가능'으로 표현된다.")
    @Test
    void describesImminentWhenPositionIsZero() {
        // given
        EstimatedWait wait = EstimatedWait.of(0L, 180.0);

        // when & then
        assertThat(wait.imminent()).isTrue();
        assertThat(wait.description()).isEqualTo("곧 주문 가능");
    }

    @DisplayName("1분 미만의 대기는 올림하여 '약 1분'으로 표현된다.")
    @Test
    void roundsUpSubMinuteWaitToAboutOneMinute() {
        // given - 360 / 180 = 2초 (1분 미만)
        EstimatedWait wait = EstimatedWait.of(360L, 180.0);

        // when & then
        assertThat(wait.minutes()).isEqualTo(1L);
        assertThat(wait.description()).isEqualTo("약 1분");
    }

    @DisplayName("순번을 처리량으로 나눈 예상 대기가 '약 N분'으로 표현된다.")
    @Test
    void describesAboutNMinutes() {
        // given - 21600 / 180 = 120초 = 2분
        EstimatedWait wait = EstimatedWait.of(21600L, 180.0);

        // when & then
        assertThat(wait.minutes()).isEqualTo(2L);
        assertThat(wait.description()).isEqualTo("약 2분");
    }

    @DisplayName("처리량이 0 이하면 예상 대기를 계산할 수 없어 예외가 발생한다.")
    @Test
    void rejectsNonPositiveThroughput() {
        // when & then
        assertThrows(CoreException.class, () -> EstimatedWait.of(100L, 0.0));
    }
}
