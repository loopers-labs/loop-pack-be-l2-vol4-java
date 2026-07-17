package com.loopers.ranking.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

/**
 * catalog-events 발행 → ranking-catalog 컨슈머 → 랭킹 ZSET 반영까지 실제 파이프라인 검증.
 * latest offset 구독 race 는 파티션 할당을 기다린 뒤 1회만 발행해 피한다.
 */
@SpringBootTest
class RankingCatalogConsumerE2ETest {

    private static final int CATALOG_EVENTS_PARTITIONS = 1;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @TestConfiguration
    static class TestKafkaConfig {

        @Bean
        NewTopic catalogEventsTopicForTest() {
            return TopicBuilder.name(KafkaTopic.CATALOG_EVENTS).partitions(CATALOG_EVENTS_PARTITIONS).replicas(1).build();
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
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RedisCleanUp redisCleanUp;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("발행된 조회 이벤트가 소비되어 랭킹 ZSET 에 가중치 점수(0.1)로 반영된다")
    void givenPublishedViewEvent_whenConsumed_thenRankingScored() throws Exception {
        awaitRankingCatalogAssigned();

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt-rank-e2e-1");
        event.put("productId", 100L);
        event.put("type", "VIEW");
        event.put("delta", 1);
        event.put("occurredAt", ZonedDateTime.now());
        stringKafkaTemplate.send(KafkaTopic.CATALOG_EVENTS, "100", objectMapper.writeValueAsString(event)).get();

        String key = "ranking:all:" + LocalDate.now(SEOUL).format(DateTimeFormatter.BASIC_ISO_DATE);
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Double score = redisTemplate.opsForZSet().score(key, "100");
            assertThat(score).isNotNull().isCloseTo(0.1, within(1e-9));
        });
    }

    private void awaitRankingCatalogAssigned() {
        MessageListenerContainer container = registry.getListenerContainers().stream()
                .filter(c -> "ranking-catalog".equals(c.getGroupId()))
                .findFirst().orElseThrow();
        ContainerTestUtils.waitForAssignment(container, CATALOG_EVENTS_PARTITIONS);
    }
}
