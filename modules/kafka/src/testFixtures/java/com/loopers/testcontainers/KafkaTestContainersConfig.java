package com.loopers.testcontainers;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 Kafka 브로커(Testcontainers). 컨테이너를 한 번 띄우고 bootstrap-servers를
 * 시스템 프로퍼티로 주입해 application.yml의 값을 덮어쓴다. (MySqlTestContainersConfig와 동일 패턴)
 */
@Configuration
public class KafkaTestContainersConfig {

    private static final KafkaContainer kafkaContainer;

    static {
        kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
        kafkaContainer.start();

        String bootstrapServers = kafkaContainer.getBootstrapServers();
        // 프로듀서/컨슈머/어드민 모두 테스트 브로커를 바라보게 한다.
        System.setProperty("spring.kafka.bootstrap-servers", bootstrapServers);
        System.setProperty("spring.kafka.admin.properties.bootstrap.servers", bootstrapServers);
    }
}
