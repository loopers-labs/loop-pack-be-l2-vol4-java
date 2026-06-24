package com.loopers.testcontainers;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합/E2E 테스트용 Kafka 브로커. MySqlTestContainersConfig와 동일하게 @Configuration 컴포넌트 스캔으로
 * @SpringBootTest 컨텍스트에 자동 로드되며, static 블록에서 컨테이너를 한 번 띄워 전 테스트가 공유한다.
 *
 * <p>좋아요 카운터가 비동기(Kafka 발행)로 바뀌면서 commerce-api 컨텍스트가 KafkaTemplate/KafkaAdmin을
 * 로드한다. 실제 브로커가 없으면 AFTER_COMMIT 발행과 토픽 생성이 연결 재시도로 지연되므로, 테스트에서도
 * 진짜 브로커를 띄워 발행 경로를 그대로 검증한다(KRaft 단일 노드, apache/kafka 네이티브 이미지).
 */
@Configuration
public class KafkaTestContainersConfig {

    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.8.0"));

    static {
        KAFKA.start();
        System.setProperty("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
    }
}
