package com.loopers.coupon.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.coupon.domain.CouponIssueRequest;
import com.loopers.coupon.domain.CouponIssueRequestRepository;
import com.loopers.outbox.domain.OutboxEvent;
import com.loopers.outbox.domain.OutboxEventRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * 선착순 쿠폰 발급 "요청" 접수. 실제 발급은 Consumer 가 한다.
 * 요청 레코드 저장과 Kafka 발행을 한 트랜잭션으로 묶기 위해 Outbox 에 적재한다(릴레이가 발행).
 * → 요청만 저장되고 메시지는 안 나가거나, 메시지만 나가고 요청이 없는 dual-write 불일치를 막는다.
 */
@Service
@RequiredArgsConstructor
public class CouponIssueRequestService {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public String request(Long userId, Long couponId) {
        String requestId = UUID.randomUUID().toString();
        couponIssueRequestRepository.save(CouponIssueRequest.pending(requestId, couponId, userId));

        CouponIssueRequestedMessage message =
                new CouponIssueRequestedMessage(requestId, couponId, userId, ZonedDateTime.now());
        outboxEventRepository.save(OutboxEvent.pending(
                KafkaTopic.COUPON_ISSUE_REQUESTS,
                String.valueOf(couponId), // 파티션 key = couponId → 같은 쿠폰을 한 파티션에서 순차 처리
                "CouponIssueRequested",
                serialize(message)
        ));
        return requestId;
    }

    @Transactional(readOnly = true)
    public CouponIssueRequestResult getResult(String requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
        return new CouponIssueRequestResult(request.getRequestId(), request.getStatus(), request.getReason());
    }

    private String serialize(CouponIssueRequestedMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "CouponIssueRequestedMessage 직렬화 실패");
        }
    }
}
