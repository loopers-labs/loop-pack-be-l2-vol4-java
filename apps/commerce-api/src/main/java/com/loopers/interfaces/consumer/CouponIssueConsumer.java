package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponTemplateService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final CouponTemplateService couponTemplateService;
    private final UserCouponRepository userCouponRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "coupon-issue-requests",
        groupId = "commerce-api-coupon",
        containerFactory = KafkaConfig.STRING_LISTENER
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String eventId = record.partition() + "-" + record.offset();
        try {
            process(eventId, record.value());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[CouponIssueConsumer] 처리 실패: eventId={}", eventId, e);
        }
    }

    @Transactional
    public void process(String eventId, String payload) throws Exception {
        JsonNode data = objectMapper.readTree(payload);
        Long memberId = data.get("memberId").asLong();
        Long templateId = data.get("templateId").asLong();

        var template = couponTemplateService.getById(templateId);

        long issuedCount = userCouponRepository.countByTemplateId(templateId);
        if (template.isSoldOut(issuedCount)) {
            log.info("[CouponIssueConsumer] 수량 초과로 발급 불가: templateId={}, memberId={}", templateId, memberId);
            return;
        }

        if (userCouponRepository.existsByMemberIdAndTemplateId(memberId, templateId)) {
            log.info("[CouponIssueConsumer] 중복 발급 요청 무시: templateId={}, memberId={}", templateId, memberId);
            return;
        }

        userCouponRepository.save(new UserCouponModel(memberId, templateId));
    }
}
