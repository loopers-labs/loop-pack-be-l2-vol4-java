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

// markHandled의 @Modifying 쿼리는 EventHandledService의 @Transactional에 합류해야 실행 가능하므로
// Repository를 직접 호출하지 않고 Service를 통해 호출한다.
@SpringBootTest
class EventHandledServiceIntegrationTest {

    @Autowired
    private EventHandledService eventHandledService;

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
            boolean result = eventHandledService.markHandled(eventId);

            // then
            assertThat(result).isTrue();
        }

        @DisplayName("같은 eventId로 두 번 호출하면 두 번째는 false를 반환한다.")
        @Test
        void returnsFalse_whenEventIdIsAlreadyHandled() {
            // given
            String eventId = UUID.randomUUID().toString();
            eventHandledService.markHandled(eventId);

            // when
            boolean result = eventHandledService.markHandled(eventId);

            // then
            assertThat(result).isFalse();
        }
    }
}
