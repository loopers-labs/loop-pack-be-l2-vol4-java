package com.loopers.interfaces.event;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.loopers.interfaces.event.order.OrderCreatedEvent;
import com.loopers.interfaces.event.product.ProductViewedEvent;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.AopTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserActionLoggingHandlerTest {

    @Autowired
    private UserActionLoggingHandler userActionLoggingHandler;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(UserActionLoggingHandler.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(UserActionLoggingHandler.class);
        logger.detachAppender(listAppender);
    }

    @DisplayName("유저 행동 로그를 기록할 때,")
    @Nested
    class Handle {

        @DisplayName("ProductViewedEvent가 발생하면, productId와 memberId가 로그에 기록된다.")
        @Test
        void handle_logsProductViewed() {
            // arrange
            ProductViewedEvent event = new ProductViewedEvent(10L, 2L);

            // act
            AopTestUtils.<UserActionLoggingHandler>getTargetObject(userActionLoggingHandler).handle(event);

            // assert
            assertThat(listAppender.list)
                .anyMatch(e -> e.getFormattedMessage().contains("10") && e.getFormattedMessage().contains("2"));
        }

        @DisplayName("OrderCreatedEvent가 발생하면, orderId와 memberId가 로그에 기록된다.")
        @Test
        void handle_logsOrderCreated() {
            // arrange
            OrderCreatedEvent event = new OrderCreatedEvent(5L, 3L, 50000L, List.of());

            // act
            AopTestUtils.<UserActionLoggingHandler>getTargetObject(userActionLoggingHandler).handle(event);

            // assert
            assertThat(listAppender.list)
                .anyMatch(e -> e.getFormattedMessage().contains("5") && e.getFormattedMessage().contains("3"));
        }
    }
}
