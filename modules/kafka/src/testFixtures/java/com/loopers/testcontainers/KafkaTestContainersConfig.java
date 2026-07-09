package com.loopers.testcontainers;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 단일 Kafka(KRaft) 컨테이너. {@code MySqlTestContainersConfig} 미러.
 *
 * <p>JVM 당 한 번 기동해 재사용하고, 컨테이너의 실제 bootstrap 주소를 <b>static 블록</b>에서 시스템 프로퍼티로
 * 심는다(생성자에서 set 하면 단독 실행 시 {@code KafkaProperties} 바인딩보다 늦어 적용이 누락된다).
 * 시스템 프로퍼티는 application.yml 보다 우선순위가 높아 {@code kafka.yml} 의 값을 덮어쓴다.
 * admin 은 test 프로파일에서 별도 bootstrap 을 두지 않아 이 값을 그대로 상속한다.</p>
 */
@Configuration
public class KafkaTestContainersConfig {

    private static final KafkaContainer KAFKA_CONTAINER =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    static {
        KAFKA_CONTAINER.start();
        System.setProperty("spring.kafka.bootstrap-servers", KAFKA_CONTAINER.getBootstrapServers());
    }
}
