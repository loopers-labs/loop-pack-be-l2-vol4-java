package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

// 컨슈머의 실제 발급 처리 로직만 검증한다 - Kafka를 거치지 않고 process()를 직접 호출해
// event_handled 멱등성 + 수량 예약 + 실제 발급 반영을 결정적으로 테스트한다(Kafka 파티션 순서 보장과는 무관한 관심사).
@SpringBootTest
class CouponIssueProcessorIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private CouponIssueProcessor couponIssueProcessor;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private CouponIssueRequestRepository couponIssueRequestRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponTemplateModel saveTemplate(int totalQuantity) {
        return couponTemplateRepository.save(new CouponTemplateModel(
                "선착순 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                ZonedDateTime.now().plusDays(30), totalQuantity));
    }

    private CouponIssueRequestModel saveRequest(Long couponTemplateId, Long userId) {
        return couponIssueRequestRepository.save(
                new CouponIssueRequestModel(UUID.randomUUID().toString(), couponTemplateId, userId));
    }

    @DisplayName("발급 요청을 처리할 때,")
    @Nested
    class Process {

        @DisplayName("수량 여유가 있으면 쿠폰이 발급되고 요청 상태가 ISSUED로 반영된다.")
        @Test
        void issuesCouponAndMarksRequestIssued_whenQuantityIsAvailable() {
            // given
            CouponTemplateModel template = saveTemplate(1);
            CouponIssueRequestModel request = saveRequest(template.getId(), USER_ID);
            String eventId = UUID.randomUUID().toString();

            // when
            couponIssueProcessor.process(eventId, request.getRequestId(), template.getId(), USER_ID);

            // then
            CouponIssueRequestModel result = couponIssueRequestRepository.findByRequestId(request.getRequestId()).orElseThrow();
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED),
                    () -> assertThat(result.getIssuedCouponId()).isNotNull(),
                    () -> assertThat(issuedCouponRepository.findAllByUserId(USER_ID)).hasSize(1),
                    () -> assertThat(couponTemplateRepository.findById(template.getId()).orElseThrow().getIssuedQuantity())
                            .isEqualTo(1)
            );
        }

        @DisplayName("수량이 소진되었으면 발급되지 않고 요청 상태가 FAILED로 반영된다.")
        @Test
        void marksRequestFailed_whenQuantityIsExhausted() {
            // given: 총 수량 1개를 다른 사용자가 먼저 소진시킨다.
            CouponTemplateModel template = saveTemplate(1);
            Long firstUserId = 2L;
            CouponIssueRequestModel firstRequest = saveRequest(template.getId(), firstUserId);
            couponIssueProcessor.process(UUID.randomUUID().toString(), firstRequest.getRequestId(), template.getId(), firstUserId);

            CouponIssueRequestModel request = saveRequest(template.getId(), USER_ID);
            String eventId = UUID.randomUUID().toString();

            // when
            couponIssueProcessor.process(eventId, request.getRequestId(), template.getId(), USER_ID);

            // then
            CouponIssueRequestModel result = couponIssueRequestRepository.findByRequestId(request.getRequestId()).orElseThrow();
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED),
                    () -> assertThat(result.getFailureReason()).isNotBlank(),
                    () -> assertThat(issuedCouponRepository.findAllByUserId(USER_ID)).isEmpty()
            );
        }

        @DisplayName("같은 eventId를 두 번 처리해도 쿠폰은 한 번만 발급된다(멱등성).")
        @Test
        void isIdempotent_whenSameEventProcessedTwice() {
            // given
            CouponTemplateModel template = saveTemplate(5);
            CouponIssueRequestModel request = saveRequest(template.getId(), USER_ID);
            String eventId = UUID.randomUUID().toString();

            // when
            couponIssueProcessor.process(eventId, request.getRequestId(), template.getId(), USER_ID);
            couponIssueProcessor.process(eventId, request.getRequestId(), template.getId(), USER_ID);

            // then
            assertAll(
                    () -> assertThat(issuedCouponRepository.findAllByUserId(USER_ID)).hasSize(1),
                    () -> assertThat(couponTemplateRepository.findById(template.getId()).orElseThrow().getIssuedQuantity())
                            .isEqualTo(1)
            );
        }
    }
}
