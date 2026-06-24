package com.loopers.testcontainers;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * commerce-streamer 통합 테스트용 Kafka 브로커(Testcontainers, KRaft 단일 노드).
 * @SpringBootTest 컴포넌트 스캔으로 자동 로드되며 static 블록에서 한 번 띄운다.
 *
 * <p>발행측(commerce-api)이 만드는 토픽을 테스트 브로커에도 만들어야 컨슈머가 구독하고 producer가
 * 보낼 수 있다(auto.create.topics.enable=false). NewTopic 빈으로 KafkaAdmin이 생성한다.
 */
@Configuration
public class KafkaTestContainersConfig {

    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.8.0"));

    static {
        KAFKA.start();
        System.setProperty("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
    }

    @Bean
    public NewTopic likeChangedTestTopic(@Value("${like-events.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
