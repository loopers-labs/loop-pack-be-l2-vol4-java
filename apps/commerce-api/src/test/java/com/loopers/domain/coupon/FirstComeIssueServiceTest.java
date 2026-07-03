package com.loopers.domain.coupon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FirstComeIssueServiceTest {

    private FakeCouponTemplateRepository templateRepo;
    private FakeUserCouponRepository userCouponRepo;
    private FakeCouponIssueRequestRepository requestRepo;
    private FirstComeIssueService service;

    @BeforeEach
    void setUp() {
        templateRepo = new FakeCouponTemplateRepository();
        userCouponRepo = new FakeUserCouponRepository();
        requestRepo = new FakeCouponIssueRequestRepository();
        service = new FirstComeIssueService(templateRepo, userCouponRepo, requestRepo);
    }

    private CouponTemplate limitedTemplate(long stock) {
        return templateRepo.save(new CouponTemplate(
            "1만원", CouponType.FIXED, 10_000L, 0L,
            LocalDateTime.now().plusDays(30), stock
        ));
    }

    private String enqueue(Long userId, Long templateId) {
        String rid = UUID.randomUUID().toString();
        requestRepo.save(new CouponIssueRequest(rid, userId, templateId));
        return rid;
    }

    @DisplayName("발급 성공")
    @Nested
    class Success {

        @DisplayName("첫 발급 요청은 ISSUED 로 마감되고 UserCoupon 이 생성된다.")
        @Test
        void issuesOne() {
            CouponTemplate t = limitedTemplate(10);
            String rid = enqueue(100L, t.getId());

            FirstComeIssueService.IssueResult result = service.processRequest(rid);

            assertThat(result.status()).isEqualTo(CouponIssueStatus.ISSUED);
            assertThat(result.userCouponId()).isNotNull();
            assertThat(templateRepo.find(t.getId()).orElseThrow().getIssuedCount()).isEqualTo(1);
        }
    }

    @DisplayName("거절")
    @Nested
    class Rejection {

        @DisplayName("재고 소진 시 REJECTED_SOLD_OUT.")
        @Test
        void soldOut() {
            CouponTemplate t = limitedTemplate(1);
            service.processRequest(enqueue(100L, t.getId())); // 1개 소진

            FirstComeIssueService.IssueResult result = service.processRequest(enqueue(200L, t.getId()));

            assertThat(result.status()).isEqualTo(CouponIssueStatus.REJECTED_SOLD_OUT);
        }

        @DisplayName("같은 유저 중복 발급은 REJECTED_DUPLICATE — 재고에 영향 없음.")
        @Test
        void duplicate() {
            CouponTemplate t = limitedTemplate(10);
            service.processRequest(enqueue(100L, t.getId()));

            FirstComeIssueService.IssueResult result = service.processRequest(enqueue(100L, t.getId()));

            assertThat(result.status()).isEqualTo(CouponIssueStatus.REJECTED_DUPLICATE);
            assertThat(templateRepo.find(t.getId()).orElseThrow().getIssuedCount())
                .isEqualTo(1); // 재고 소진되지 않음
        }
    }

    @DisplayName("멱등")
    @Nested
    class Idempotency {

        @DisplayName("같은 requestId 로 두 번째 processRequest 는 이전 결과를 그대로 반환한다.")
        @Test
        void sameRequestIdIsIdempotent() {
            CouponTemplate t = limitedTemplate(10);
            String rid = enqueue(100L, t.getId());
            FirstComeIssueService.IssueResult first = service.processRequest(rid);

            FirstComeIssueService.IssueResult second = service.processRequest(rid);

            assertThat(second.status()).isEqualTo(first.status());
            assertThat(second.userCouponId()).isEqualTo(first.userCouponId());
            assertThat(templateRepo.find(t.getId()).orElseThrow().getIssuedCount())
                .isEqualTo(1); // 두 번 카운트되지 않음
        }
    }

    @DisplayName("100명 정원, 200 요청 시 정확히 100건만 ISSUED, 100건은 REJECTED_SOLD_OUT.")
    @Test
    void exactly100IssuedFrom200Requests() {
        CouponTemplate t = limitedTemplate(100);

        int issued = 0, soldOut = 0;
        for (long userId = 1; userId <= 200; userId++) {
            String rid = enqueue(userId, t.getId());
            FirstComeIssueService.IssueResult result = service.processRequest(rid);
            if (result.status() == CouponIssueStatus.ISSUED) issued++;
            else if (result.status() == CouponIssueStatus.REJECTED_SOLD_OUT) soldOut++;
        }

        assertThat(issued).isEqualTo(100);
        assertThat(soldOut).isEqualTo(100);
        assertThat(templateRepo.find(t.getId()).orElseThrow().getIssuedCount()).isEqualTo(100);
    }
}
