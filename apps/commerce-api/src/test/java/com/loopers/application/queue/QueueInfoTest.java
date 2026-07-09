package com.loopers.application.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class QueueInfoTest {

    @DisplayName("forEnter로 생성할 때,")
    @Nested
    class ForEnter {

        @DisplayName("totalWaiting/estimatedWaitSeconds/token은 모두 null이다.")
        @Test
        void setsTotalWaitingAndEstimatedWaitSecondsAndTokenToNull() {
            // when
            QueueInfo info = QueueInfo.forEnter(3L);

            // then
            assertAll(
                    () -> assertThat(info.position()).isEqualTo(3L),
                    () -> assertThat(info.totalWaiting()).isNull(),
                    () -> assertThat(info.estimatedWaitSeconds()).isNull(),
                    () -> assertThat(info.token()).isNull()
            );
        }
    }

    @DisplayName("forPosition으로 생성할 때,")
    @Nested
    class ForPosition {

        @DisplayName("전달된 값이 그대로 채워진다.")
        @Test
        void fillsAllFieldsWithGivenValues() {
            // when
            QueueInfo info = QueueInfo.forPosition(128L, 1500L, 45L, "abc-123-def");

            // then
            assertAll(
                    () -> assertThat(info.position()).isEqualTo(128L),
                    () -> assertThat(info.totalWaiting()).isEqualTo(1500L),
                    () -> assertThat(info.estimatedWaitSeconds()).isEqualTo(45L),
                    () -> assertThat(info.token()).isEqualTo("abc-123-def")
            );
        }
    }
}
