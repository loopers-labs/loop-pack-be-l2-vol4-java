package com.loopers.testcontainers;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Configuration
public class KafkaTestContainersConfig {

  private static final KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

  static {
    kafkaContainer.start();
    System.setProperty("spring.kafka.bootstrap-servers", kafkaContainer.getBootstrapServers());
  }
}
