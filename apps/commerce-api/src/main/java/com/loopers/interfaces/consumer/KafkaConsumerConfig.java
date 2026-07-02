package com.loopers.interfaces.consumer;

import com.loopers.confg.kafka.KafkaConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * commerce-api 의 <b>첫 consumer</b> 인프라. api 는 지금까지 outbox relay 로 produce 만 했으나, 선착순 쿠폰 발급은
 * 도메인 불변식(issuedCount &lt; issueLimit · 1인 1매)을 소유한 이 앱이 직접 소비해야 하므로 여기에 record 리스너
 * 팩토리와 실패 격리(DLQ)를 둔다. streamer 의 {@code KafkaErrorHandlingConfig} 와 같은 정책을 미러링한다.
 *
 * <p><b>record 리스너 + 파티션 직렬화</b>: 발급은 요청 단위 TX 라 record 리스너가 맞다. concurrency=3 이지만 한
 * 템플릿(key=templateId)은 한 파티션에만 매핑돼 단일 스레드가 순차 처리하므로, 템플릿 간 병렬 · 템플릿 내 직렬이 된다.
 * 실패는 {@link FixedBackOff}(1초 ×2=총 3회) 뒤 {@code <topic>.DLT} 로 격리해 poison 이 파티션을 막지 못하게 한다.</p>
 */
@Configuration
public class KafkaConsumerConfig {

    /** 쿠폰 발급 consumer 가 쓰는 record 리스너 팩토리 이름. */
    public static final String RECORD_LISTENER = "couponRecordListenerFactory";

    /** DLQ 토픽 접미사 — 실패 레코드는 원본 토픽 + 이 접미사로 격리된다. */
    public static final String DLT_SUFFIX = ".DLT";

    /**
     * DLT 재발행 전용 템플릿. consumer value 는 byte[](ByteArrayDeserializer)이므로 원문 바이트를 그대로 실어야 한다
     * → value 직렬화기를 {@link ByteArraySerializer} 로 둔다(공유 JsonSerializer 템플릿이면 이중 인코딩).
     */
    @Bean
    public KafkaTemplate<Object, Object> couponDeadLetterKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    @Bean
    public CommonErrorHandler couponDlqErrorHandler(KafkaTemplate<Object, Object> couponDeadLetterKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                couponDeadLetterKafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        // 결정적 poison(필수값 누락/타입 위반 등)은 재시도해도 실패하므로 즉시 DLT 격리. 일시 장애만 backOff.
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    @Bean(name = RECORD_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> couponRecordListenerFactory(
            KafkaProperties kafkaProperties,
            ByteArrayJsonMessageConverter converter,
            CommonErrorHandler couponDlqErrorHandler
    ) {
        // modules:kafka 의 튜닝 상수를 재사용(숫자의 단일 정의) — batch 팩토리와 폴 루프/리밸런스 특성을 맞춘다.
        Map<String, Object> consumerConfig = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerConfig.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, KafkaConfig.MAX_POLLING_SIZE);
        consumerConfig.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, KafkaConfig.FETCH_MIN_BYTES);
        consumerConfig.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, KafkaConfig.FETCH_MAX_WAIT_MS);
        consumerConfig.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, KafkaConfig.SESSION_TIMEOUT_MS);
        consumerConfig.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, KafkaConfig.HEARTBEAT_INTERVAL_MS);
        consumerConfig.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, KafkaConfig.MAX_POLL_INTERVAL_MS);

        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfig));
        factory.setRecordMessageConverter(converter);
        factory.setConcurrency(3);
        factory.setCommonErrorHandler(couponDlqErrorHandler);
        // manual ack — 리스너가 발급 트랜잭션을 커밋한 뒤에만 명시적으로 오프셋을 커밋한다(at-least-once).
        // MANUAL_IMMEDIATE 라 acknowledge() 호출 즉시 커밋되고, 예외 경로는 acknowledge() 에 도달하지 못해
        // 에러 핸들러의 재시도→DLT 격리로 흘러간다(성공했을 때만 전진).
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
