package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 발급 소비자 핸들러의 결정적(비-Kafka) 통합 테스트 — SUCCESS/SOLD_OUT/ALREADY_ISSUED/FAILED/멱등을 직접 호출로 못박는다.
 * 동시성(파티션 직렬화)은 {@code CouponIssueConcurrencyIntegrationTest} 가 실제 Kafka 로 검증한다.
 */
@SpringBootTest
class CouponIssueApplicationServiceIntegrationTest {

    @Autowired
    private CouponIssueApplicationService couponIssueApplicationService;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long limitedTemplate(int limit) {
        return couponTemplateJpaRepository.save(
                CouponTemplate.create("선착순", DiscountPolicy.of(DiscountType.FIXED, 1_000L), 30, limit)).getId();
    }

    private CouponIssueMessage seed(String requestId, Long userId, Long templateId) {
        couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(requestId, userId, templateId));
        return new CouponIssueMessage(requestId, userId, templateId, ZonedDateTime.now());
    }

    private CouponIssueStatus statusOf(String requestId) {
        return couponIssueRequestJpaRepository.findByRequestId(requestId).orElseThrow().getStatus();
    }

    private int issuedCountOf(Long templateId) {
        return couponTemplateJpaRepository.findById(templateId).orElseThrow().getIssuedCount();
    }

    @DisplayName("한도가 남아 있으면 발급하고 SUCCESS 로 전이한다.")
    @Test
    void success_whenSlotAvailable() {
        Long templateId = limitedTemplate(2);
        couponIssueApplicationService.handle(seed("req-A", 100L, templateId));

        assertThat(statusOf("req-A")).isEqualTo(CouponIssueStatus.SUCCESS);
        assertThat(userCouponJpaRepository.findByUserIdAndTemplateId(100L, templateId)).isPresent();
        assertThat(issuedCountOf(templateId)).isEqualTo(1);
    }

    @DisplayName("한도 소진 후의 요청은 SOLD_OUT 이고 발급/카운터 증가가 없다.")
    @Test
    void soldOut_whenLimitReached() {
        Long templateId = limitedTemplate(1);
        couponIssueApplicationService.handle(seed("req-A", 100L, templateId));

        couponIssueApplicationService.handle(seed("req-B", 200L, templateId));

        assertThat(statusOf("req-B")).isEqualTo(CouponIssueStatus.SOLD_OUT);
        assertThat(userCouponJpaRepository.findByUserIdAndTemplateId(200L, templateId)).isEmpty();
        assertThat(issuedCountOf(templateId)).isEqualTo(1);
    }

    @DisplayName("이미 발급받은 유저의 재요청은 ALREADY_ISSUED 이고 슬롯을 소모하지 않는다.")
    @Test
    void alreadyIssued_whenAlreadyHasCoupon() {
        Long templateId = limitedTemplate(5);
        couponIssueApplicationService.handle(seed("req-A", 100L, templateId));

        couponIssueApplicationService.handle(seed("req-A2", 100L, templateId));

        assertThat(statusOf("req-A2")).isEqualTo(CouponIssueStatus.ALREADY_ISSUED);
        assertThat(issuedCountOf(templateId)).as("중복은 슬롯 미소모").isEqualTo(1);
    }

    @DisplayName("템플릿이 존재하지 않으면(결정적 실패) FAILED 로 전이한다.")
    @Test
    void failed_whenTemplateMissing() {
        long missingTemplateId = 999_999L;
        couponIssueApplicationService.handle(seed("req-X", 100L, missingTemplateId));

        assertThat(statusOf("req-X")).isEqualTo(CouponIssueStatus.FAILED);
        assertThat(userCouponJpaRepository.findByUserIdAndTemplateId(100L, missingTemplateId)).isEmpty();
    }

    @DisplayName("이미 처리된 요청을 재전달해도(멱등) 두 번 발급하지 않는다.")
    @Test
    void idempotent_whenRedelivered() {
        Long templateId = limitedTemplate(5);
        CouponIssueMessage message = seed("req-A", 100L, templateId);
        couponIssueApplicationService.handle(message);

        couponIssueApplicationService.handle(message);

        assertThat(statusOf("req-A")).isEqualTo(CouponIssueStatus.SUCCESS);
        assertThat(issuedCountOf(templateId)).as("재전달로 카운터가 두 번 증가하면 안 된다").isEqualTo(1);
        assertThat(userCouponJpaRepository.count()).isEqualTo(1);
    }
}
