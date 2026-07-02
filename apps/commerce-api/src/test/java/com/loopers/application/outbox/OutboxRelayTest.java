package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxRelay relay = new OutboxRelay(repository, template);

    @DisplayName("PENDING 을 발행하고 SENT 로 표시(save)한다.")
    @Test
    void publishesAndMarksSent() {
        OutboxEvent e = OutboxEvent.pending("evt-1", "catalog-events", "100", "LikeAdded", "{}");
        when(repository.findPendingBatch()).thenReturn(List.of(e));
        when(template.send(eq("catalog-events"), eq("100"), eq("{}")))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.relayOnce();

        assertThat(e.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(repository).save(e);
    }

    @DisplayName("발행 실패 시 PENDING 유지(save 안 함).")
    @Test
    void keepsPendingOnFailure() {
        OutboxEvent e = OutboxEvent.pending("evt-1", "catalog-events", "100", "LikeAdded", "{}");
        when(repository.findPendingBatch()).thenReturn(List.of(e));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(template.send(any(), any(), any())).thenReturn(failed);

        relay.relayOnce();

        assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
