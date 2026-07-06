package com.loopers.support.dlq;

import com.loopers.confg.kafka.KafkaTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 깨진(역직렬화 불가) 메시지가 order-events.DLT 로 격리되는지 검증한다.
 * latest 구독 race 를 피하려 awaitility 안에서 같은 깨진 메시지를 반복 발행한다(DLT 는 누적되어도 무방).
 */
@SpringBootTest
class DeadLetterE2ETest {

    @TestConfiguration
    static class TestKafkaConfig {
        @Bean
        NewTopic orderEventsTopicForDltTest() {
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

    private KafkaConsumer<String, String> dltConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("spring.kafka.bootstrap-servers"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    @Test
    @DisplayName("역직렬화 불가 메시지는 order-events.DLT 로 격리된다")
    void givenMalformedMessage_whenConsumed_thenRoutedToDlt() {
        String malformed = "{ this is not valid json";
        String dltTopic = KafkaTopic.deadLetterOf(KafkaTopic.ORDER_EVENTS);

        try (KafkaConsumer<String, String> dlt = dltConsumer()) {
            dlt.subscribe(List.of(dltTopic));

            await().atMost(Duration.ofSeconds(25)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
                stringKafkaTemplate.send(KafkaTopic.ORDER_EVENTS, "1", malformed);
                stringKafkaTemplate.flush();

                ConsumerRecords<String, String> records = dlt.poll(Duration.ofMillis(500));
                boolean found = false;
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value().contains("not valid json")) {
                        found = true;
                    }
                }
                assertThat(found).isTrue();
            });
        }
    }
}
