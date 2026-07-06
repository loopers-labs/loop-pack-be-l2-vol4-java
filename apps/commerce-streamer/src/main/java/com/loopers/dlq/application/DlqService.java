package com.loopers.dlq.application;

import com.loopers.dlq.domain.DlqMessage;
import com.loopers.dlq.domain.DlqMessageStatus;
import com.loopers.dlq.infrastructure.DlqMessageJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * DLQ 운영. 담당자가 격리 메시지를 조회하고, 원인을 고친 뒤 재처리(원본 토픽 재발행)하거나 폐기한다.
 * retry 는 "수동 재발행"이지 자동 무한재시도가 아니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqService {

    private final DlqMessageJpaRepository dlqMessageJpaRepository;
    private final KafkaTemplate<String, String> dltKafkaTemplate;

    @Transactional(readOnly = true)
    public Page<DlqMessage> list(String topic, DlqMessageStatus status, Pageable pageable) {
        if (topic != null && status != null) {
            return dlqMessageJpaRepository.findByOriginalTopicAndStatus(topic, status, pageable);
        }
        if (topic != null) {
            return dlqMessageJpaRepository.findByOriginalTopic(topic, pageable);
        }
        if (status != null) {
            return dlqMessageJpaRepository.findByStatus(status, pageable);
        }
        return dlqMessageJpaRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public DlqMessage get(Long id) {
        return find(id);
    }

    @Transactional
    public void retry(Long id) {
        DlqMessage message = find(id);
        dltKafkaTemplate.send(message.getOriginalTopic(), message.getMessageKey(), message.getPayload());
        message.markRetried();
        log.info("DLQ 재처리(원본 토픽 재발행) id={} topic={}", id, message.getOriginalTopic());
    }

    @Transactional
    public void discard(Long id) {
        find(id).markDiscarded();
        log.info("DLQ 폐기 id={}", id);
    }

    private DlqMessage find(Long id) {
        return dlqMessageJpaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DLQ 메시지를 찾을 수 없습니다. id=" + id));
    }
}
