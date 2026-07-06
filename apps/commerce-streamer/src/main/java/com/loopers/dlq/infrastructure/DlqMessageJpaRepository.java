package com.loopers.dlq.infrastructure;

import com.loopers.dlq.domain.DlqMessage;
import com.loopers.dlq.domain.DlqMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DlqMessageJpaRepository extends JpaRepository<DlqMessage, Long> {

    Page<DlqMessage> findByStatus(DlqMessageStatus status, Pageable pageable);

    Page<DlqMessage> findByOriginalTopic(String originalTopic, Pageable pageable);

    Page<DlqMessage> findByOriginalTopicAndStatus(String originalTopic, DlqMessageStatus status, Pageable pageable);
}
