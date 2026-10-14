package com.loopers.domain.eventpublish;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxMessageTest {

    private OutboxMessage newMessage() {
        return OutboxMessage.create(
            "Order", "42", "OrderCompleted",
            "order-events", "42", "{\"orderId\":42}"
        );
    }

    @DisplayName("생성")
    @Nested
    class Create {

        @DisplayName("정상 생성 시 PENDING 상태 + eventId(UUID) 가 부여된다.")
        @Test
        void createsPendingWithEventId() {
            OutboxMessage message = newMessage();

            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getEventId()).isNotBlank();
            assertThat(message.getRetryCount()).isZero();
            assertThat(message.getSentAt()).isNull();
        }

        @DisplayName("필수 필드가 비면 BAD_REQUEST.")
        @Test
        void missingFieldsThrow() {
            assertThatThrownBy(() -> OutboxMessage.create(
                null, "1", "T", "topic", "1", "{}"))
                .isInstanceOf(CoreException.class);
            assertThatThrownBy(() -> OutboxMessage.create(
                "Order", "1", "T", "topic", "1", ""))
                .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("상태 전이")
    @Nested
    class Transition {

        @DisplayName("markSent 는 SENT 로 전이 + sentAt 을 채운다.")
        @Test
        void markSentTransitions() {
            OutboxMessage message = newMessage();
            ZonedDateTime now = ZonedDateTime.now();

            message.markSent(now);

            assertThat(message.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(message.getSentAt()).isEqualTo(now);
        }

        @DisplayName("markSent 는 멱등 — 이미 SENT 면 재호출해도 시각 변경 없음.")
        @Test
        void markSentIsIdempotent() {
            OutboxMessage message = newMessage();
            ZonedDateTime first = ZonedDateTime.now();
            message.markSent(first);

            message.markSent(first.plusHours(1));

            assertThat(message.getSentAt()).isEqualTo(first);
        }

        @DisplayName("markFailed 는 PENDING 유지 + retryCount 증가 + lastError 기록.")
        @Test
        void markFailedIncrementsRetry() {
            OutboxMessage message = newMessage();

            message.markFailed("timeout");
            message.markFailed("broker unavailable");

            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getRetryCount()).isEqualTo(2);
            assertThat(message.getLastError()).isEqualTo("broker unavailable");
        }
    }
}
