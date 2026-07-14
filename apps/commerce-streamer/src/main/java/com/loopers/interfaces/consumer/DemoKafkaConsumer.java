package com.loopers.interfaces.consumer;

import com.loopers.confg.kafka.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class DemoKafkaConsumer {
    @KafkaListener(
        topics = {"${demo-kafka.test.topic-name}"},
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void demoListener(
        List<ConsumerRecord<Object,Object>> messages,
        Acknowledgment acknowledgment
    ){
        log.info("Received demo kafka messages. size={}", messages.size());
        acknowledgment.acknowledge();
    }
}
