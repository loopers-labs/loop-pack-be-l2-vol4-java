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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 발급 파이프 <b>전 구간</b>을 실제 Kafka(Testcontainer)로 못박는 동시성 테스트.
 *
 * <p>N(=200)명이 동시에 발급 요청을 접수(각자 PENDING request + outbox 행) → OutboxRelay 가 coupon-issue-requests 로
 * key=templateId 발행 → 같은 키라 한 파티션 → 단일 소비자 스레드가 순차 처리. 락도 원자 UPDATE 도 없이 파티션
 * 직렬화만으로 정확히 limit(=100) 장만 발급되고 나머지는 SOLD_OUT 이어야 한다(초과 발급 0).</p>
 */
@SpringBootTest
class CouponIssueConcurrencyIntegrationTest {

    @Autowired
    private CouponApplicationService couponApplicationService;

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

    @DisplayName("limit=100 인 템플릿에 200명이 동시에 발급 요청해도, 정확히 100장만 발급되고 100건은 SOLD_OUT 이 된다(초과 0).")
    @Test
    void firstComeFirstServed_issuesExactlyLimit() throws InterruptedException {
        int limit = 100;
        int users = 200;
        Long templateId = couponTemplateJpaRepository.save(
                CouponTemplate.create("선착순 100명", DiscountPolicy.of(DiscountType.FIXED, 1_000L), 30, limit)).getId();

        // N명이 동시에 접수(접수 자체는 유저별 독립이라 경합 없음 — 경합은 소비자 단일 스레드에서 직렬화된다).
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(users);
        for (int i = 0; i < users; i++) {
            final long userId = 1_000L + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    couponApplicationService.requestIssue(userId, templateId);
                } catch (Exception ignored) {
                    // 접수 실패는 아래 수렴 단언에서 미달로 드러난다.
                } finally {
                    doneGate.countDown();
                }
            });
        }
        startGate.countDown();
        assertThat(doneGate.await(30, TimeUnit.SECONDS)).as("모든 접수가 30초 내에 끝나야 한다").isTrue();
        executor.shutdownNow();

        // 비동기 발급이 모든 요청을 터미널 상태로 수렴시킬 때까지 대기(relay 폴링 + Kafka + 소비자).
        Map<CouponIssueStatus, Long> counts = awaitAllTerminal(users);

        assertThat(counts.getOrDefault(CouponIssueStatus.SUCCESS, 0L)).as("발급은 정확히 한도만큼").isEqualTo(limit);
        assertThat(counts.getOrDefault(CouponIssueStatus.SOLD_OUT, 0L)).as("나머지는 SOLD_OUT").isEqualTo(users - limit);
        assertThat(counts.getOrDefault(CouponIssueStatus.PENDING, 0L)).as("미처리 잔여가 없어야 한다").isZero();

        assertThat(couponTemplateJpaRepository.findById(templateId).orElseThrow().getIssuedCount())
                .as("issuedCount 는 한도를 넘지 않는다").isEqualTo(limit);
        assertThat(userCouponJpaRepository.count()).as("실제 발급된 쿠폰 수도 정확히 한도").isEqualTo(limit);
    }

    /** 모든(=users) 요청이 터미널 상태로 수렴할 때까지 폴링하고, 상태별 개수를 돌려준다(최대 ~60초). */
    private Map<CouponIssueStatus, Long> awaitAllTerminal(int users) {
        Map<CouponIssueStatus, Long> counts = Map.of();
        for (int i = 0; i < 600; i++) {
            List<CouponIssueRequest> all = couponIssueRequestJpaRepository.findAll();
            counts = all.stream().collect(Collectors.groupingBy(CouponIssueRequest::getStatus, Collectors.counting()));
            long terminal = all.stream().filter(r -> r.getStatus() != CouponIssueStatus.PENDING).count();
            if (all.size() == users && terminal == users) {
                return counts;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return counts;
    }
}
