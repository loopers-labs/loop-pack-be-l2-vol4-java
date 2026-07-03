package com.loopers.domain.eventpublish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxServiceTest {

    private FakeOutboxRepository repo;
    private OutboxService service;

    @BeforeEach
    void setUp() {
        repo = new FakeOutboxRepository();
        service = new OutboxService(repo);
    }

    private OutboxMessage make(String aggId, String type) {
        return OutboxMessage.create(
            "Product", aggId, type,
            "catalog-events", aggId, "{\"a\":1}"
        );
    }

    @DisplayName("append 후 loadPendingBatch 는 저장 순서대로 PENDING 만 반환한다.")
    @Test
    void loadsPendingInOrder() {
        service.append(make("1", "LikeChanged"));
        OutboxMessage second = service.append(make("2", "ProductViewed"));
        service.markSent(second.getId());

        List<OutboxMessage> batch = service.loadPendingBatch(10);

        assertThat(batch).hasSize(1);
        assertThat(batch.get(0).getAggregateId()).isEqualTo("1");
    }

    @DisplayName("markSent 후 다음 배치 조회에서 제외된다.")
    @Test
    void sentExcludedFromBatch() {
        OutboxMessage m = service.append(make("1", "LikeChanged"));

        service.markSent(m.getId());

        assertThat(service.loadPendingBatch(10)).isEmpty();
    }

    @DisplayName("markFailed 는 PENDING 을 유지해 다음 폴링에서 다시 조회된다.")
    @Test
    void failedRemainsPending() {
        OutboxMessage m = service.append(make("1", "LikeChanged"));

        service.markFailed(m.getId(), "broker down");

        List<OutboxMessage> batch = service.loadPendingBatch(10);
        assertThat(batch).hasSize(1);
        assertThat(batch.get(0).getRetryCount()).isEqualTo(1);
    }
}
