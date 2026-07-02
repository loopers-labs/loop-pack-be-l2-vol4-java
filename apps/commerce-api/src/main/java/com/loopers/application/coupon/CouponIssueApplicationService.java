package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 선착순 쿠폰 발급 소비자의 유스케이스 — coupon-issue-requests 한 건을 발급 결과로 확정한다. (도메인 불변식을 소유한
 * commerce-api 가 직접 호스팅.)
 *
 * <p><b>동시성</b>: 파티션 직렬화(key=templateId → 한 템플릿은 한 파티션 → 단일 소비자 스레드)를 전제로
 * {@code issuedCount < issueLimit} 를 락/원자 UPDATE 없이 강제한다. 멱등 판정·중복 검사·발급·상태 전이를
 * <b>한 트랜잭션</b>에 묶어, 커밋 시 요청 상태 전이와 발급·카운터 증가가 함께 확정된다(부분 반영 없음).</p>
 *
 * <p>처리 순서: (1) 멱등(request 상태) → (2) 중복 유저(슬롯 미소모, ALREADY_ISSUED) → (3) 결정적 실패(템플릿 삭제/무제한,
 * FAILED) → (4) 한도 소진(SOLD_OUT) → (5) 발급(SUCCESS). 일시 장애는 예외를 <b>삼키지 않고</b> 전파해 재시도→DLQ 로
 * 흘려보낸다(FAILED 로 확정하지 않는다 — 재시도로 복구되어야 하므로).</p>
 */
@Component
@RequiredArgsConstructor
public class CouponIssueApplicationService {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public void handle(CouponIssueMessage message) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(message.requestId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "발급 요청을 찾을 수 없습니다. (requestId: " + message.requestId() + ")"));

        // 메시지는 "어느 요청을 처리하라"는 포인터(requestId)로만 신뢰한다. 실제 처리 대상(userId·templateId)은
        // 외부 경계인 Kafka payload 가 아니라 DB 요청 행을 진실로 삼는다 — 수동 재처리·payload 훼손 시에도
        // "요청 행과 발급 결과가 갈리는" 무결성 균열을 구조적으로 차단한다.
        Long userId = request.getUserId();
        Long templateId = request.getTemplateId();

        // (1) 멱등 — 이미 처리된 요청의 재전달이면 조용히 skip.
        if (!request.isPending()) {
            return;
        }

        // (2) 1인 1매 — 이미 발급받은 유저면 슬롯을 소모하지 않고 ALREADY_ISSUED. (user_coupons unique 가 최후 방어선.)
        if (userCouponRepository.findByUserIdAndTemplateId(userId, templateId).isPresent()) {
            request.markAlreadyIssued();
            return;
        }

        // (3) 결정적 실패 — 템플릿이 삭제됐거나 선착순 대상이 아니면 재시도해도 성공 못 하므로 FAILED.
        Optional<CouponTemplate> found = couponTemplateRepository.find(templateId);
        if (found.isEmpty() || !found.get().isLimited()) {
            request.markFailed();
            return;
        }
        CouponTemplate template = found.get();

        // (4) 한도 소진 — 예상된 결과라 예외가 아닌 반환값(false)으로 판정.
        if (!template.issueOne()) {
            request.markSoldOut();
            return;
        }

        // (5) 발급 — 슬롯을 확보했으니 쿠폰을 발급하고 요청을 SUCCESS 로 전이.
        userCouponRepository.save(UserCoupon.issue(userId, template, ZonedDateTime.now()));
        request.markSuccess();
    }
}
