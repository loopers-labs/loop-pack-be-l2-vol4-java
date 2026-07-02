package com.loopers.testcontainers;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Kafka 통합 테스트용 컨테이너. 테스트에서 @DynamicPropertySource 로
 * spring.kafka.bootstrap-servers 에 getBootstrapServers() 를 주입한다.
 * (@Configuration 이 아니라 컴포넌트 스캔되지 않음 — Kafka 불필요한 @SpringBootTest 는 브로커를 안 켬)
 */
public final class KafkaTestContainersConfig {

    private static KafkaContainer kafkaContainer;

    private KafkaTestContainersConfig() {}

    public static synchronized void ensureStarted() {
        if (kafkaContainer == null) {
            kafkaContainer = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
            kafkaContainer.start();
        }
    }

    public static String getBootstrapServers() {
        ensureStarted();
        return kafkaContainer.getBootstrapServers();
    }
}
