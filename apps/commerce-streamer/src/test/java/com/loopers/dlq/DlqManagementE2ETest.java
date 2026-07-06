package com.loopers.dlq;

import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.dlq.application.DlqService;
import com.loopers.dlq.domain.DlqMessage;
import com.loopers.dlq.domain.DlqMessageStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 깨진(역직렬화 불가) 메시지가 → DLT 격리 → dlq_message 적재 → API 조회 → 재처리(RETRIED) 되는 운영 흐름 검증.
 */
@SpringBootTest
class DlqManagementE2ETest {

    @TestConfiguration
    static class TestKafkaConfig {
        @Bean
        NewTopic orderEventsTopicForDlqTest() {
            return TopicBuilder.name(KafkaTopic.ORDER_EVENTS).partitions(3).replicas(1).build();
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
    private DlqService dlqService;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("깨진 메시지가 DLQ에 적재되어 토픽·에러로 조회되고, 재처리하면 RETRIED 가 된다")
    void malformedMessage_isIngestedAndManageable() {
        String malformed = "{ this is broken json";

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            stringKafkaTemplate.send(KafkaTopic.ORDER_EVENTS, "1", malformed);
            stringKafkaTemplate.flush();

            Page<DlqMessage> page = dlqService.list(KafkaTopic.ORDER_EVENTS, DlqMessageStatus.NEW, PageRequest.of(0, 10));
            assertThat(page.getContent()).isNotEmpty();
        });

        DlqMessage message = dlqService.list(KafkaTopic.ORDER_EVENTS, DlqMessageStatus.NEW, PageRequest.of(0, 1))
                .getContent().get(0);
        assertThat(message.getOriginalTopic()).isEqualTo(KafkaTopic.ORDER_EVENTS);
        assertThat(message.getExceptionClass()).isNotBlank();
        assertThat(message.getPayload()).contains("broken");

        dlqService.retry(message.getId());

        assertThat(dlqService.get(message.getId()).getStatus()).isEqualTo(DlqMessageStatus.RETRIED);
    }
}
