package com.loopers.config.kafka;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class RankingKafkaConfig {

  public static final String ACTIVITY_TOPIC = "commerce.product-activity.v1";
  public static final String DLT_TOPIC = ACTIVITY_TOPIC + ".DLT";
  public static final String RANKING_GROUP_ID = "commerce-ranking-v1";
  public static final String RECORD_LISTENER_FACTORY = "rankingRecordListenerContainerFactory";
  public static final long RETRY_INTERVAL_MILLIS = 1_000L;
  public static final long RETRY_ATTEMPTS = 2L;

  private static final int TOPIC_PARTITIONS = 3;
  private static final short TOPIC_REPLICAS = 1;

  @Bean
  public NewTopic productActivityTopic() {
    return TopicBuilder.name(ACTIVITY_TOPIC)
        .partitions(TOPIC_PARTITIONS)
        .replicas(TOPIC_REPLICAS)
        .build();
  }

  @Bean
  public NewTopic productActivityDltTopic() {
    return TopicBuilder.name(DLT_TOPIC)
        .partitions(TOPIC_PARTITIONS)
        .replicas(TOPIC_REPLICAS)
        .build();
  }

  @Bean
  public ProducerFactory<String, byte[]> rankingDltProducerFactory(
      KafkaProperties kafkaProperties) {
    Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
    properties.put(
        org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class);
    properties.put(
        org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        ByteArraySerializer.class);
    return new DefaultKafkaProducerFactory<>(properties);
  }

  @Bean
  public KafkaTemplate<String, byte[]> rankingDltKafkaTemplate(
      ProducerFactory<String, byte[]> rankingDltProducerFactory) {
    return new KafkaTemplate<>(rankingDltProducerFactory);
  }

  @Bean(name = RECORD_LISTENER_FACTORY)
  public ConcurrentKafkaListenerContainerFactory<String, byte[]>
      rankingRecordListenerContainerFactory(
          KafkaProperties kafkaProperties, KafkaTemplate<String, byte[]> rankingDltKafkaTemplate) {
    Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
    factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(properties));
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

    var recoverer =
        new DeadLetterPublishingRecoverer(
            rankingDltKafkaTemplate,
            (record, exception) -> new TopicPartition(DLT_TOPIC, record.partition()));
    var errorHandler =
        new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, RETRY_ATTEMPTS));
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }
}
