package com.loopers.application.coupon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.kafka.event.CouponIssueRequestEventPayload;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Service
public class CouponCommandService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CouponResult.Template createTemplate(CouponCommand.CreateTemplate command) {
        if (command == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 등록 요청은 필수입니다.");
        }
        return CouponResult.Template.from(couponTemplateRepository.save(new CouponTemplate(
            command.name(),
            command.type(),
            command.value(),
            command.minOrderAmount(),
            command.totalIssueLimit(),
            command.maxIssuesPerUser(),
            command.expiredAt()
        )));
    }

    @Transactional
    public CouponResult.Template updateTemplate(Long couponTemplateId, CouponCommand.UpdateTemplate command) {
        validateCouponTemplateId(couponTemplateId);
        if (command == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 수정 요청은 필수입니다.");
        }

        CouponTemplate couponTemplate = getTemplate(couponTemplateId);
        couponTemplate.update(
            command.name(),
            command.type(),
            command.value(),
            command.minOrderAmount(),
            command.totalIssueLimit(),
            command.maxIssuesPerUser(),
            command.expiredAt()
        );
        return CouponResult.Template.from(couponTemplateRepository.save(couponTemplate));
    }

    @Transactional
    public void deleteTemplate(Long couponTemplateId) {
        CouponTemplate couponTemplate = getTemplate(couponTemplateId);
        couponTemplate.delete();
        couponTemplateRepository.save(couponTemplate);
    }

    @Transactional
    public CouponResult.Issued issue(Long couponTemplateId, String userId) {
        validateCouponTemplateId(couponTemplateId);
        validateUserId(userId);

        IssuedCouponWithTemplate issued = issueInternal(couponTemplateId, userId);
        return CouponResult.Issued.from(issued.issuedCoupon(), issued.couponTemplate().getName(), ZonedDateTime.now());
    }

    @Transactional
    public CouponResult.IssueRequest requestIssue(Long couponTemplateId, String userId) {
        validateCouponTemplateId(couponTemplateId);
        validateUserId(userId);

        CouponTemplate couponTemplate = couponTemplateRepository.find(couponTemplateId)
            .orElseThrow(() -> new CoreException(
                ErrorType.NOT_FOUND,
                "[id = " + couponTemplateId + "] 쿠폰 템플릿을 찾을 수 없습니다."
            ));
        couponTemplate.ensureIssuable(ZonedDateTime.now());

        CouponIssueRequest request = couponIssueRequestRepository.save(new CouponIssueRequest(couponTemplateId, userId));
        eventOutboxRepository.save(new EventOutbox(
            EventOutbox.TOPIC_COUPON_ISSUE_REQUESTS,
            String.valueOf(couponTemplateId),
            EventOutbox.EVENT_COUPON_ISSUE_REQUESTED,
            EventOutbox.AGGREGATE_COUPON_TEMPLATE,
            String.valueOf(couponTemplateId),
            serialize(new CouponIssueRequestEventPayload(
                request.getId(),
                couponTemplateId,
                userId,
                request.getCreatedAt()
            ))
        ));

        return CouponResult.IssueRequest.from(request);
    }

    @Transactional
    public CouponResult.IssueRequest processIssueRequest(Long requestId) {
        validateRequestId(requestId);

        CouponIssueRequest request = couponIssueRequestRepository.findForUpdate(requestId)
            .orElseThrow(() -> new CoreException(
                ErrorType.NOT_FOUND,
                "[id = " + requestId + "] 쿠폰 발급 요청을 찾을 수 없습니다."
            ));
        if (!request.isPending()) {
            return CouponResult.IssueRequest.from(request);
        }

        try {
            IssuedCouponWithTemplate issued = issueInternal(request.getCouponTemplateId(), request.getUserId());
            request.succeed(issued.issuedCoupon().getId());
        } catch (CoreException e) {
            request.fail(e.getMessage());
        }

        return CouponResult.IssueRequest.from(couponIssueRequestRepository.save(request));
    }

    private IssuedCouponWithTemplate issueInternal(Long couponTemplateId, String userId) {
        CouponTemplate couponTemplate = couponTemplateRepository.findActiveForUpdate(couponTemplateId)
            .orElseThrow(() -> new CoreException(
                ErrorType.NOT_FOUND,
                "[id = " + couponTemplateId + "] 발급 가능한 쿠폰 템플릿을 찾을 수 없습니다."
            ));
        couponTemplate.ensureIssuable(ZonedDateTime.now());

        long issuedCount = issuedCouponRepository.countByCouponTemplateIdAndUserId(couponTemplateId, userId);
        if (issuedCount >= couponTemplate.getMaxIssuesPerUser()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자별 쿠폰 발급 한도를 초과했습니다.");
        }

        if (couponTemplate.hasTotalIssueLimit()) {
            long totalIssuedCount = issuedCouponRepository.countByCouponTemplateId(couponTemplateId);
            if (totalIssuedCount >= couponTemplate.getTotalIssueLimit()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 수량이 모두 소진되었습니다.");
            }
        }

        IssuedCoupon issuedCoupon = issuedCouponRepository.save(new IssuedCoupon(couponTemplateId, userId, couponTemplate));
        return new IssuedCouponWithTemplate(issuedCoupon, couponTemplate);
    }

    private CouponTemplate getTemplate(Long couponTemplateId) {
        validateCouponTemplateId(couponTemplateId);
        return couponTemplateRepository.find(couponTemplateId)
            .orElseThrow(() -> new CoreException(
                ErrorType.NOT_FOUND,
                "[id = " + couponTemplateId + "] 쿠폰 템플릿을 찾을 수 없습니다."
            ));
    }

    private void validateCouponTemplateId(Long couponTemplateId) {
        if (couponTemplateId == null || couponTemplateId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 필수입니다.");
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private void validateRequestId(Long requestId) {
        if (requestId == null || requestId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 요청 ID는 필수입니다.");
        }
    }

    private String serialize(CouponIssueRequestEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 요청 이벤트 payload 생성에 실패했습니다.");
        }
    }

    private record IssuedCouponWithTemplate(IssuedCoupon issuedCoupon, CouponTemplate couponTemplate) {
    }
}
