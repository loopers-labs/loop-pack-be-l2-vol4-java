package com.loopers.domain.eventhandled;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventHandledRepositoryIntegrationTest {

    @Autowired
    private EventHandledRepository eventHandledRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("markHandled를 호출할 때,")
    @Nested
    class MarkHandled {

        @DisplayName("처음 처리하는 eventId면 true를 반환한다.")
        @Test
        void returnsTrue_whenEventIdIsFirstSeen() {
            // given
            String eventId = UUID.randomUUID().toString();

            // when
            boolean result = eventHandledRepository.markHandled(eventId);

            // then
            assertThat(result).isTrue();
        }

        @DisplayName("같은 eventId로 두 번 호출하면 두 번째는 false를 반환한다.")
        @Test
        void returnsFalse_whenEventIdIsAlreadyHandled() {
            // given
            String eventId = UUID.randomUUID().toString();
            eventHandledRepository.markHandled(eventId);

            // when
            boolean result = eventHandledRepository.markHandled(eventId);

            // then
            assertThat(result).isFalse();
        }
    }
}
