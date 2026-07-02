package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxRelay outboxRelay;

    private OutboxModel pending(String eventId, Long aggregateId) {
        return OutboxModel.of(eventId, "product", aggregateId, "catalog-events", "LIKE_CHANGED", "{\"eventId\":\"" + eventId + "\"}");
    }

    @DisplayName("발행이 성공하면, 해당 아웃박스를 PUBLISHED 로 마킹하고 저장한다.")
    @Test
    void marksPublished_whenSendSucceeds() {
        // given
        OutboxModel row = pending("evt-1", 100L);
        given(outboxRepository.findPending(anyInt())).willReturn(List.of(row));
        given(kafkaTemplate.send(eq("catalog-events"), eq("100"), any()))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        // when
        outboxRelay.publishPending();

        // then
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(outboxRepository).save(row);
    }

    @DisplayName("발행이 실패하면, 마킹하지 않고(PENDING 유지) 저장하지 않아 다음 주기에 재시도된다.")
    @Test
    void keepsPending_whenSendFails() {
        // given
        OutboxModel row = pending("evt-2", 200L);
        given(outboxRepository.findPending(anyInt())).willReturn(List.of(row));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        given(kafkaTemplate.send(any(), any(), any())).willReturn(failed);

        // when
        outboxRelay.publishPending();

        // then
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxRepository, never()).save(any());
    }
}