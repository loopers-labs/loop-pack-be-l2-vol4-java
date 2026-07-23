package com.loopers.tddstudy.application.coupon;

import com.loopers.tddstudy.infrastructure.coupon.CouponIssueResult;
import com.loopers.tddstudy.infrastructure.coupon.CouponIssueResultJpaRepository;
import com.loopers.tddstudy.messaging.CouponIssueRequested;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CouponRequestService {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final CouponIssueResultJpaRepository resultRepository;

    public CouponRequestService(KafkaTemplate<Object, Object> kafkaTemplate,
                                CouponIssueResultJpaRepository resultRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.resultRepository = resultRepository;
    }

    @Transactional
    public String request(Long couponId, Long userId) {
        String requestId = UUID.randomUUID().toString();
        resultRepository.save(new CouponIssueResult(requestId, couponId, userId));  // PENDING
        kafkaTemplate.send(CouponIssueRequested.TOPIC, String.valueOf(couponId),
                new CouponIssueRequested(requestId, couponId, userId, System.currentTimeMillis()));
        return requestId;
    }

    @Transactional(readOnly = true)
    public CouponIssueResult getResult(String requestId) {
        return resultRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("발급 요청을 찾을 수 없습니다."));
    }
}
