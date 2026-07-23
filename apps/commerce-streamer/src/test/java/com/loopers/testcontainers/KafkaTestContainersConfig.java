package com.loopers.testcontainers;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * commerce-streamer 통합 테스트용 Kafka 브로커 컨테이너.
 * modules/kafka testFixtures가 아닌 streamer 테스트 소스에 두는 이유:
 * commerce-api 통합 테스트까지 실제 브로커에 붙으면 Outbox 스케줄러가 정말로 발행을 시작해
 * 기존 테스트들의 전제(발행 실패를 조용히 무시)가 바뀌기 때문 — 컨슘 검증이 필요한 streamer만 사용한다.
 */
@Configuration
public class KafkaTestContainersConfig {

    private static final KafkaContainer kafkaContainer =
        new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    static {
        kafkaContainer.start();
        // 시스템 프로퍼티는 static 블록에서 설정해야 한다 — 생성자에서 설정하면
        // KafkaProperties 바인딩이 먼저 일어난 뒤일 수 있어 반영되지 않는다 (MySql/RedisTestContainersConfig와 동일 패턴)
        System.setProperty("spring.kafka.bootstrap-servers", kafkaContainer.getBootstrapServers());
    }
}
