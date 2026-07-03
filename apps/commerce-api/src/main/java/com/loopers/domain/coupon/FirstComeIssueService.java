package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * 선착순 발급 유스케이스 — Consumer 가 직접 부르는 도메인 조립 서비스.
 * <p>
 * 한 트랜잭션 안에서:
 * <ol>
 *   <li>템플릿 조회 (선착순은 partition key 로 순차 처리라 락 불필요, 다중 인스턴스 대비 방어로 향후 findForUpdate 검토)</li>
 *   <li>중복 발급 확인 → REJECTED_DUPLICATE</li>
 *   <li>도메인 CAS (issueOne) → REJECTED_SOLD_OUT 가능</li>
 *   <li>UserCoupon 생성</li>
 *   <li>발급 요청 상태 전이 → ISSUED / REJECTED_*</li>
 * </ol>
 * 결과 상태는 리턴값으로 반환한다. 예외는 시스템 에러로만 사용 — 비즈니스 거절은 리턴값으로.
 */
@RequiredArgsConstructor
@Component
public class FirstComeIssueService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;

    @Transactional
    public IssueResult processRequest(String requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[requestId = " + requestId + "] 발급 요청을 찾을 수 없습니다."));

        if (request.getStatus().isTerminal()) {
            return new IssueResult(request.getStatus(), request.getUserCouponId(), request.getRejectReason());
        }

        CouponTemplate template = couponTemplateRepository.find(request.getCouponTemplateId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + request.getCouponTemplateId() + "] 쿠폰 템플릿을 찾을 수 없습니다."));

        ZonedDateTime now = ZonedDateTime.now();

        if (template.isExpiredAt(LocalDateTime.now())) {
            request.markRejected(CouponIssueStatus.REJECTED_INVALID, "만료된 쿠폰입니다.", now);
            couponIssueRequestRepository.save(request);
            return new IssueResult(CouponIssueStatus.REJECTED_INVALID, null, "만료된 쿠폰입니다.");
        }

        if (userCouponRepository.existsByUserIdAndCouponTemplateId(request.getUserId(), request.getCouponTemplateId())) {
            request.markRejected(CouponIssueStatus.REJECTED_DUPLICATE, "이미 발급받은 쿠폰입니다.", now);
            couponIssueRequestRepository.save(request);
            return new IssueResult(CouponIssueStatus.REJECTED_DUPLICATE, null, "이미 발급받은 쿠폰입니다.");
        }

        try {
            template.issueOne();
        } catch (CoreException soldOut) {
            request.markRejected(CouponIssueStatus.REJECTED_SOLD_OUT, soldOut.getMessage(), now);
            couponIssueRequestRepository.save(request);
            return new IssueResult(CouponIssueStatus.REJECTED_SOLD_OUT, null, soldOut.getMessage());
        }
        couponTemplateRepository.save(template);

        UserCoupon issued = userCouponRepository.save(
            new UserCoupon(request.getUserId(), request.getCouponTemplateId(), LocalDateTime.now())
        );

        request.markIssued(issued.getId(), now);
        couponIssueRequestRepository.save(request);
        return new IssueResult(CouponIssueStatus.ISSUED, issued.getId(), null);
    }

    public record IssueResult(CouponIssueStatus status, Long userCouponId, String reason) {}
}
