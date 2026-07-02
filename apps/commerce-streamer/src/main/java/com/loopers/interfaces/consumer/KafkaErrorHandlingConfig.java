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
 * consumer 실패 격리 정책 = <b>재시도 후 DLQ</b>. metrics collector 전용 <b>record 리스너 팩토리</b>에 이
 * {@link CommonErrorHandler} 를 장착한다.
 *
 * <p><b>왜 record 리스너인가</b>: 배치 리스너 + 변환된 타입({@code List<CatalogEventMessage>}) 조합에서는
 * {@code BatchListenerFailedException(index)} 가 원본 {@code ConsumerRecord} 로 매핑되지 못해 per-record 복구가
 * 걸리지 않고 배치가 무한 재시도된다. record 리스너면 {@code DefaultErrorHandler} + {@code DeadLetterPublishingRecoverer}
 * 가 레코드 단위로 자연스럽게 동작한다(집계는 어차피 레코드 단위 TX라 배치 이점이 거의 없었다).</p>
 *
 * <p>레코드 처리가 실패하면 {@link FixedBackOff}(1초, 2회 재시도 = 총 3회) 소진 후 원문을 {@code <topic>.DLT} 로 격리하고
 * offset 을 전진시킨다 → poison 메시지가 파티션을 영구 정지시키지 못한다. 일시 장애(재시도로 복구)와 poison(결정적)을
 * 시간축으로 구분한다. producer relay 의 무한 재시도(브로커 일시장애 대응)와는 다른 축이다.</p>
 */
@Configuration
public class KafkaErrorHandlingConfig {

    /** 모든 record collector(catalog·order)가 공유하는 record 리스너 팩토리 이름. */
    public static final String RECORD_LISTENER = "recordListenerFactory";

    /** 선착순 발급 consumer 전용 record 리스너 팩토리 이름(manual ack). */
    public static final String COUPON_RECORD_LISTENER = "couponRecordListenerFactory";

    /** DLQ 토픽 접미사. 실패 레코드는 원본 토픽 이름 + 이 접미사로 격리된다(소스별 자동 분기). */
    public static final String DLT_SUFFIX = ".DLT";

    /** catalog-events 의 DLQ 토픽. {@link KafkaTopicConfig} 의 NewTopic 과 반드시 같은 이름이어야 한다(상수로 고정). */
    public static final String CATALOG_EVENTS_DLT = CatalogEventConsumer.CATALOG_EVENTS + DLT_SUFFIX;

    /** order-events 의 DLQ 토픽. */
    public static final String ORDER_EVENTS_DLT = OrderSalesConsumer.ORDER_EVENTS + DLT_SUFFIX;

    /** coupon-issue-requests 의 DLQ 토픽. */
    public static final String COUPON_ISSUE_REQUESTS_DLT = CouponIssueConsumer.COUPON_ISSUE_REQUESTS + DLT_SUFFIX;

    /**
     * DLT 재발행 전용 템플릿. consumer 의 value 는 byte[](ByteArrayDeserializer)이므로 원문 바이트를 그대로 실어야 한다
     * → value 직렬화기를 {@link ByteArraySerializer} 로 둔다(공유 JsonSerializer 템플릿이면 이중 인코딩).
     */
    @Bean
    public KafkaTemplate<Object, Object> deadLetterKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    @Bean
    public CommonErrorHandler dlqErrorHandler(KafkaTemplate<Object, Object> deadLetterKafkaTemplate) {
        // 목적지를 소스 토픽 기준으로 분기한다: <원본토픽>.DLT, 파티션 = -1(producer 가 결정).
        // 이 팩토리를 catalog·order collector 가 공유하므로, 소스 토픽에서 DLT 이름을 유도해야 격리가 뒤섞이지 않는다.
        // (기본 리졸버는 소스 파티션 번호를 그대로 지정해, 발행 시점에 producer 메타데이터가 없으면 유실될 수 있다.)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterKafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        // 결정적 poison(필수값 누락/타입 위반 등 IllegalArgumentException 계열)은 재시도해도 반드시 실패한다
        // → 재시도 없이 즉시 DLT 격리해 파이프라인 지연을 줄인다. 일시 장애(DB 순단 등)만 backOff 를 태운다.
        // (역직렬화/변환 실패인 DeserializationException·MessageConversionException 등은 DefaultErrorHandler 가
        //  이미 기본 not-retryable 로 취급한다 — 여기에 도메인 판정 축을 하나 더 얹는 것.)
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    /**
     * catalog·order collector 가 공유하는 record 리스너 팩토리. value 는 byte[](ByteArrayDeserializer)로 받고
     * {@link ByteArrayJsonMessageConverter} 로 리스너 파라미터 타입(CatalogEventMessage/OrderEventMessage)으로 변환한다.
     * 컨테이너가 offset 을 관리(AckMode.RECORD)하므로 정상 처리 시 커밋, 예외 시 미커밋→에러핸들러가 재시도/DLT 후
     * 커밋한다(at-least-once). DLT 목적지는 소스 토픽에서 유도되므로 두 collector 가 안전하게 공유한다.
     */
    @Bean(name = RECORD_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> recordListenerFactory(
            KafkaProperties kafkaProperties,
            ByteArrayJsonMessageConverter converter,
            CommonErrorHandler dlqErrorHandler
    ) {
        // batch 팩토리(KafkaConfig)가 코드로 얹던 consumer 튜닝을 record 팩토리에도 동일하게 맞춘다(같은 상수 재사용
        // = 숫자의 단일 정의). YAML(buildConsumerProperties)만 쓰면 이 값들이 빠져 두 팩토리의 폴 루프/리밸런스 특성이
        // 달라진다. record 리스너는 건당 처리라 max.poll.records 가 커도 한 건 처리시간이 짧아 interval 압박은 덜하다.
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
        factory.setCommonErrorHandler(dlqErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    /**
     * 선착순 발급 consumer 전용 팩토리 — collector 팩토리와 튜닝·에러 핸들러(재시도→DLT)는 공유하되,
     * ack 를 <b>manual</b>({@link ContainerProperties.AckMode#MANUAL_IMMEDIATE})로 둔다. 발급 트랜잭션이 커밋된 뒤
     * consumer 가 명시적으로 {@code acknowledge()} 할 때만 오프셋이 전진한다(성공 시에만 전진 = at-least-once).
     * {@code dlqErrorHandler} 는 토픽에 무관하게 {@code <원본토픽>.DLT} 로 격리하므로 그대로 재사용한다.
     */
    @Bean(name = COUPON_RECORD_LISTENER)
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> couponRecordListenerFactory(
            KafkaProperties kafkaProperties,
            ByteArrayJsonMessageConverter converter,
            CommonErrorHandler dlqErrorHandler
    ) {
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
        factory.setCommonErrorHandler(dlqErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
