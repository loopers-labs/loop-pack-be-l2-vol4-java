package com.loopers.application.dataplatform;

import com.loopers.domain.dataplatform.DataPlatformPayload;
import com.loopers.domain.dataplatform.DataPlatformSender;
import com.loopers.domain.order.event.OrderPlaced;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

@SpringBootTest
class DataPlatformEventIntegrationTest {

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private PlatformTransactionManager txManager;
    @MockitoBean private DataPlatformSender dataPlatformSender; // LoggingDataPlatformSender 를 mock 으로 대체
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("커밋 후 OrderPlaced 를 @Async 리스너가 받아 데이터플랫폼으로 전송한다.")
    @Test
    void sendsAfterCommit() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        OrderPlaced event = new OrderPlaced(100L, 7L, 2000L,
            List.of(new OrderPlaced.Line(11L, 2)), ZonedDateTime.now());

        tx.executeWithoutResult(s -> eventPublisher.publishEvent(event));

        ArgumentCaptor<DataPlatformPayload> captor = ArgumentCaptor.forClass(DataPlatformPayload.class);
        await().atMost(3, TimeUnit.SECONDS)
            .untilAsserted(() -> verify(dataPlatformSender).send(captor.capture()));
        assertThat(captor.getValue().orderId()).isEqualTo(100L);
    }
}
