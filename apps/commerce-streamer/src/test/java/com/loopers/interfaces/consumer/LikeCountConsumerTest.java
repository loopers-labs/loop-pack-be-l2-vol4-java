package com.loopers.interfaces.consumer;

import com.loopers.infrastructure.product.ProductLikesUpdater;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 컨슈머의 배치 합산(coalescing) 로직 단위 테스트 — Kafka/DB 없이 검증한다.
 * 같은 상품의 +1/-1을 하나의 델타로 합쳐 updater에 넘기는지, 처리 후 ack 하는지가 핵심.
 */
class LikeCountConsumerTest {

    @DisplayName("같은 상품의 +1/-1을 합산해 상품별 델타 1건으로 updater에 넘기고, ack 한다.")
    @Test
    void coalescesDeltasPerProduct() {
        ProductLikesUpdater updater = mock(ProductLikesUpdater.class);
        Acknowledgment ack = mock(Acknowledgment.class);
        LikeCountConsumer consumer = new LikeCountConsumer(updater);

        // 상품 10: +1 +1 +1 -1 = +2 / 상품 20: +1 = +1
        List<LikeChangedMessage> batch = List.of(
                new LikeChangedMessage(10L, +1),
                new LikeChangedMessage(10L, +1),
                new LikeChangedMessage(20L, +1),
                new LikeChangedMessage(10L, +1),
                new LikeChangedMessage(10L, -1)
        );

        consumer.consume(batch, ack);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Long>> captor = ArgumentCaptor.forClass(Map.class);
        verify(updater).applyDeltas(captor.capture());
        assertThat(captor.getValue())
                .containsEntry(10L, 2L)
                .containsEntry(20L, 1L)
                .hasSize(2);
        verify(ack).acknowledge();
    }
}
