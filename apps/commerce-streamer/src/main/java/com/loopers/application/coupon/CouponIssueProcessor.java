package com.loopers.application.coupon;

import com.loopers.infrastructure.coupon.CouponIssueJdbcRepository;
import com.loopers.infrastructure.coupon.CouponIssueJdbcRepository.RequestRow;
import com.loopers.infrastructure.coupon.CouponIssueJdbcRepository.TemplateRow;
import com.loopers.interfaces.consumer.CouponIssueMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 선착순 발급 유스케이스 — coupon-issue-requests 한 건을 발급 결과로 확정한다(commerce-streamer 호스팅).
 *
 * <p><b>동시성</b>: key=templateId 파티션 직렬화(한 템플릿=한 파티션=단일 스레드)를 전제로 하되, 슬롯 차감은
 * 조건부 원자 UPDATE({@link CouponIssueJdbcRepository#tryConsumeSlot})라 파티션 가정이 깨져도 초과 발급이 없다.
 * 멱등 판정·중복·차감·발급·상태 전이를 <b>한 트랜잭션</b>에 묶어 부분 반영을 막는다.</p>
 *
 * <p>처리 순서: (1) 멱등(요청 상태) → (2) 중복 유저(슬롯 미소모, ALREADY_ISSUED) → (3) 결정적 실패(템플릿 삭제/무제한,
 * FAILED) → (4) 한도 소진(SOLD_OUT) → (5) 발급(SUCCESS). 처리 대상(userId·templateId)은 외부 경계인 Kafka payload 가
 * 아니라 <b>DB 요청 행</b>을 진실로 삼는다. 일시 장애는 예외를 전파해 재시도→DLQ 로 흘려보낸다(FAILED 로 확정하지 않는다).</p>
 */
@Component
@RequiredArgsConstructor
public class CouponIssueProcessor {

    private final CouponIssueJdbcRepository couponIssueJdbcRepository;

    @Transactional
    public void handle(CouponIssueMessage message) {
        // 메시지는 requestId 포인터로만 신뢰한다. 요청 행이 없으면 결정적 이상(발급 요청은 발행 전에 커밋됨)
        // → 재시도 무의미하므로 IllegalArgumentException 으로 즉시 DLT 로 보낸다.
        RequestRow request = couponIssueJdbcRepository.findRequest(message.requestId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "발급 요청을 찾을 수 없습니다. (requestId: " + message.requestId() + ")"));

        // (1) 멱등 — 이미 처리된 요청의 재전달이면 조용히 skip.
        if (!"PENDING".equals(request.status())) {
            return;
        }

        Long userId = request.userId();
        Long templateId = request.templateId();
        ZonedDateTime now = ZonedDateTime.now();

        // (2) 1인 1매 — 이미 발급받은 유저면 슬롯을 소모하지 않고 ALREADY_ISSUED. (user_coupons unique 가 최후 방어선.)
        if (couponIssueJdbcRepository.existsUserCoupon(userId, templateId)) {
            couponIssueJdbcRepository.updateStatus(message.requestId(), "ALREADY_ISSUED", now);
            return;
        }

        // (3) 결정적 실패 — 템플릿이 삭제됐거나 선착순 대상이 아니면 재시도해도 성공 못 하므로 FAILED.
        Optional<TemplateRow> found = couponIssueJdbcRepository.findTemplate(templateId);
        if (found.isEmpty() || !found.get().limited()) {
            couponIssueJdbcRepository.updateStatus(message.requestId(), "FAILED", now);
            return;
        }
        TemplateRow template = found.get();

        // (4) 한도 소진 — 조건부 원자 UPDATE 로 슬롯 확보 시도. 확보 실패면 SOLD_OUT.
        if (!couponIssueJdbcRepository.tryConsumeSlot(templateId, now)) {
            couponIssueJdbcRepository.updateStatus(message.requestId(), "SOLD_OUT", now);
            return;
        }

        // (5) 발급 — 슬롯을 확보했으니 쿠폰을 발급하고 요청을 SUCCESS 로 전이.
        couponIssueJdbcRepository.insertUserCoupon(userId, template, now);
        couponIssueJdbcRepository.updateStatus(message.requestId(), "SUCCESS", now);
    }
}
