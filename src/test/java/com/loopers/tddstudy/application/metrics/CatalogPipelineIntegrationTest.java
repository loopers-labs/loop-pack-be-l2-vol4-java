package com.loopers.tddstudy.application.metrics;

import com.loopers.tddstudy.infrastructure.metrics.ProductMetrics;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.tddstudy.messaging.CatalogEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"catalog-events"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class CatalogPipelineIntegrationTest {

    @Autowired KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired ProductMetricsJpaRepository metricsRepository;

    @Test
    void 좋아요_이벤트를_소비하면_metrics에_반영되고_중복은_무시된다() throws Exception {
        CatalogEvent event = new CatalogEvent("evt-1", "PRODUCT_LIKED", 1L, 1, System.currentTimeMillis());

        kafkaTemplate.send(CatalogEvent.TOPIC, String.valueOf(event.productId()), event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ProductMetrics m = metricsRepository.findById(1L).orElse(null);
            assertThat(m).isNotNull();
            assertThat(m.getLikeCount()).isEqualTo(1);
        });

        // 같은 event_id 재전송 → 멱등(두 배 안 됨)
        kafkaTemplate.send(CatalogEvent.TOPIC, String.valueOf(event.productId()), event);
        Thread.sleep(3000);
        assertThat(metricsRepository.findById(1L).get().getLikeCount()).isEqualTo(1);
    }
}
