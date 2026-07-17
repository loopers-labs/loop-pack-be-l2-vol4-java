package com.loopers.product.application.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogEventMessageTest {

    @DisplayName("조회 이벤트 생성 시 발생 시각(occurredAt)이 담긴다")
    @Test
    void whenViewCreated_thenOccurredAtIsStamped() {
        // Arrange
        ZonedDateTime before = ZonedDateTime.now();

        // Act
        CatalogEventMessage message = CatalogEventMessage.view(101L);

        // Assert
        assertThat(message.occurredAt()).isNotNull();
        assertThat(message.occurredAt()).isAfterOrEqualTo(before);
    }

    @DisplayName("좋아요 이벤트 생성 시 발생 시각(occurredAt)이 담긴다")
    @Test
    void whenLikeCreated_thenOccurredAtIsStamped() {
        // Arrange
        ZonedDateTime before = ZonedDateTime.now();

        // Act
        CatalogEventMessage message = CatalogEventMessage.like(101L, 1);

        // Assert
        assertThat(message.occurredAt()).isNotNull();
        assertThat(message.occurredAt()).isAfterOrEqualTo(before);
    }
}
