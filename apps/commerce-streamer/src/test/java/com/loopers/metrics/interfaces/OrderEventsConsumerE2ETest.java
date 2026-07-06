package com.loopers.metrics.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.metrics.domain.ProductMetric;
import com.loopers.metrics.infrastructure.ProductMetricJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * order-events 발행 → consumer 판매량 집계까지 실제 파이프라인 검증.
 * self-contained 이벤트라 컨슈머가 SSOT 를 읽지 않고 담긴 수량을 그대로 증분한다.
 * latest offset 구독 race 는 파티션 할당을 기다린 뒤 1회만 발행해 피한다(재발행하면 delta 가 중복 누적되므로).
 * streamer 단독 컨텍스트에는 api 의 토픽 정의가 없어 브로커가 기본 1파티션으로 만든다 → 1파티션으로 검증.
 */
@SpringBootTest
class OrderEventsConsumerE2ETest {

    private static final int ORDER_EVENTS_PARTITIONS = 1;

    @TestConfiguration
    static class TestKafkaConfig {

        @Bean
        NewTopic orderEventsTopicForTest() {
            return TopicBuilder.name(KafkaTopic.ORDER_EVENTS).partitions(ORDER_EVENTS_PARTITIONS).replicas(1).build();
        }

        @Bean
        KafkaTemplate<String, String> stringKafkaTemplate(KafkaProperties kafkaProperties) {
            Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }
    }

    @Autowired
    private KafkaTemplate<String, String> stringKafkaTemplate;
    @Autowired
    private ProductMetricJpaRepository productMetricJpaRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("발행된 결제완료 이벤트가 소비되어 담긴 수량만큼 판매량으로 집계된다")
    void givenPublishedOrderEvent_whenConsumed_thenSalesAggregated() throws Exception {
        awaitOrderConsumerAssigned();

        OrderPaidMessage message = new OrderPaidMessage(
                "evt-e2e-1", 1L,
                List.of(new OrderPaidMessage.Line(100L, 4)), ZonedDateTime.now());
        stringKafkaTemplate.send(KafkaTopic.ORDER_EVENTS, String.valueOf(message.orderId()),
                objectMapper.writeValueAsString(message)).get();

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            ProductMetric metric = productMetricJpaRepository.findById(100L).orElse(null);
            assertThat(metric).isNotNull();
            assertThat(metric.getSalesCount()).isEqualTo(4);
        });
    }

    private void awaitOrderConsumerAssigned() {
        MessageListenerContainer container = registry.getListenerContainers().stream()
                .filter(c -> "metrics-consumer".equals(c.getGroupId()))
                .findFirst().orElseThrow();
        ContainerTestUtils.waitForAssignment(container, ORDER_EVENTS_PARTITIONS);
    }
}
