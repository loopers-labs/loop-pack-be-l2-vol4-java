package com.loopers.application.coupon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.infrastructure.outbox.OutboxEvent;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.interfaces.api.config.KafkaTopicConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CouponApplicationService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final ObjectMapper objectMapper;

    /**
     * 동기 발급(무제한 템플릿 전용). 선착순 한도가 걸린 템플릿은 파티션 직렬화 경로로만 발급해야 하므로
     * 여기서 거부한다 — 그러지 않으면 이 동기 경로가 한도를 우회한다(뒷문 차단).
     */
    @Transactional
    public CouponInfo.Issued issue(Long userId, Long templateId) {
        CouponTemplate template = couponTemplateRepository.find(templateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        if (template.isLimited()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순 쿠폰은 발급 요청 경로로만 발급할 수 있습니다.");
        }

        userCouponRepository.findByUserIdAndTemplateId(userId, templateId)
                .ifPresent(existing -> {
                    throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
                });

        UserCoupon issued = UserCoupon.issue(userId, template, ZonedDateTime.now());
        UserCoupon saved = userCouponRepository.save(issued);
        return CouponInfo.Issued.from(saved);
    }

    /**
     * 선착순 발급 <b>요청 접수</b>(한도 있는 템플릿 전용). 요청 행 저장과 outbox 적재를 <b>같은 트랜잭션</b>에서 수행해
     * "요청 기록 + 전파 의도"의 원자성을 보장한다(dual-write 회피). 실제 발급은 소비자가 파티션 직렬화로 처리하고,
     * 결과는 폴링({@link #getIssueRequest})으로 확인한다. 여기서는 즉시 requestId 만 돌려준다.
     */
    @Transactional
    public CouponInfo.IssueRequested requestIssue(Long userId, Long templateId) {
        CouponTemplate template = couponTemplateRepository.find(templateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        if (!template.isLimited()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순 대상이 아닌 쿠폰입니다.");
        }

        String requestId = UUID.randomUUID().toString();
        CouponIssueRequest request = couponIssueRequestRepository.save(
                CouponIssueRequest.pending(requestId, userId, templateId));

        // outbox 행의 eventId 를 요청 requestId 와 통일한다(1 요청 = 1 행 = 1 메시지 = 1 식별자).
        CouponIssueMessage message = new CouponIssueMessage(requestId, userId, templateId, ZonedDateTime.now());
        outboxEventJpaRepository.save(OutboxEvent.pending(
                requestId, String.valueOf(templateId),
                KafkaTopicConfig.COUPON_ISSUE_REQUESTS, serialize(message)));

        return CouponInfo.IssueRequested.from(request);
    }

    /**
     * 선착순 발급 요청 상태 폴링 — 로그인 사용자 <b>본인</b>의 요청만 조회할 수 있다. 소유자가 아니면 존재 자체를
     * 노출하지 않도록 NOT_FOUND 로 응답한다(다른 사용자의 requestId 열람 차단).
     */
    @Transactional(readOnly = true)
    public CouponInfo.IssueRequestStatus getIssueRequest(Long userId, String requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId)
                .filter(it -> it.getUserId().equals(userId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
        return CouponInfo.IssueRequestStatus.from(request);
    }

    private String serialize(CouponIssueMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "outbox payload 직렬화에 실패했습니다: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResult<CouponInfo.MyCoupon> getMyCoupons(Long userId, int page, int size) {
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponRepository.findAllByUserId(userId, page, size)
                .map(coupon -> CouponInfo.MyCoupon.from(coupon, now));
    }

    @Transactional
    public CouponInfo.Template registerTemplate(CouponCriteria.RegisterTemplate command) {
        DiscountPolicy policy = DiscountPolicy.of(command.discountType(), command.discountValue(), command.minOrderAmount());
        CouponTemplate template = CouponTemplate.create(command.name(), policy, command.validDays());
        return CouponInfo.Template.from(couponTemplateRepository.save(template));
    }

    @Transactional
    public void modifyTemplate(CouponCriteria.ModifyTemplate command) {
        CouponTemplate template = couponTemplateRepository.find(command.id())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        DiscountPolicy policy = DiscountPolicy.of(command.discountType(), command.discountValue(), command.minOrderAmount());
        template.modify(command.name(), policy, command.validDays());
    }

    @Transactional
    public void deleteTemplate(Long id) {
        CouponTemplate template = couponTemplateRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        template.delete();
    }

    @Transactional(readOnly = true)
    public CouponInfo.Template getTemplate(Long id) {
        CouponTemplate template = couponTemplateRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        return CouponInfo.Template.from(template);
    }

    @Transactional(readOnly = true)
    public PageResult<CouponInfo.Template> getTemplatePage(int page, int size) {
        return couponTemplateRepository.findAll(page, size).map(CouponInfo.Template::from);
    }

    @Transactional(readOnly = true)
    public PageResult<CouponInfo.IssueHistoryItem> getIssueHistory(Long templateId, int page, int size) {
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponRepository.findAllByTemplateId(templateId, page, size)
                .map(coupon -> CouponInfo.IssueHistoryItem.from(coupon, now));
    }
}
