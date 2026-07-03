package com.loopers.testcontainers;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 단일 Kafka 브로커(KRaft) 컨테이너. testFixtures(kafka) 를 의존하는 앱의 @SpringBootTest 에서
 * component-scan 으로 기동되며, bootstrap-servers 를 컨테이너 주소로 덮어쓴다.
 * kafka.yml 의 test 프로파일이 하드코딩한 localhost:19092 / admin kafka:9092 을 모두 override 한다.
 */
@Configuration
public class KafkaTestContainersConfig {

    private static final KafkaContainer kafkaContainer;

    static {
        kafkaContainer = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
        kafkaContainer.start();

        String bootstrapServers = kafkaContainer.getBootstrapServers();
        System.setProperty("spring.kafka.bootstrap-servers", bootstrapServers);
        System.setProperty("spring.kafka.admin.properties.bootstrap.servers", bootstrapServers);
    }
}
