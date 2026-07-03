package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponConcurrencyIntegrationTest {

    private static final int THREAD_COUNT = 10;
    private static final String LOGIN_ID = "cconcur01";
    private static final String LOGIN_PW = "Password1!";

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponTemplateService couponTemplateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    // 파티션 키(couponTemplateId)로 인해 실제 Kafka 경유 흐름은 같은 쿠폰 요청이 한 파티션에서 직렬 처리된다.
    // 즉 API로 N개를 동시에 쏴도 컨슈머 단에서는 순서대로 처리되어, 원자적 가드가 없어도 우연히 테스트가 통과할 수 있다.
    // 그래서 선착순 수량 제한의 실제 방어선인 CouponTemplateService.reserveQuantity(원자적 UPDATE)를 직접 동시 호출해 검증한다.
    @DisplayName("선착순 수량이 제한된 쿠폰에 대해 동시에 발급을 예약할 때,")
    @Nested
    class ReserveQuantityConcurrency {

        @DisplayName("총 수량을 초과하는 예약 요청 중 총 수량만큼만 성공한다.")
        @Test
        void onlyTotalQuantitySucceeds_whenReservedConcurrentlyBeyondCapacity() throws InterruptedException {
            // given
            int totalQuantity = 5;
            CouponTemplateModel template = couponTemplateRepository.save(new CouponTemplateModel(
                    "선착순 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000),
                    null, ZonedDateTime.now().plusDays(30), totalQuantity));
            Long templateId = template.getId();

            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREAD_COUNT);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        if (couponTemplateService.reserveQuantity(templateId)) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }
            startGate.countDown();
            done.await();
            executor.shutdown();

            // then
            CouponTemplateModel result = couponTemplateRepository.findById(templateId).orElseThrow();
            assertAll(
                    () -> assertThat(successCount.get()).isEqualTo(totalQuantity),
                    () -> assertThat(result.getIssuedQuantity()).isEqualTo(totalQuantity)
            );
        }

        @DisplayName("관리자가 조회해 둔 템플릿을 원자적 예약 이후에 그대로 저장하면, 낙관적 락 충돌로 예약분을 덮어쓰지 않는다.")
        @Test
        void throwsOptimisticLockConflict_whenStaleTemplateIsSavedAfterConcurrentReserve() {
            // given: 관리자가 수정 화면에서 템플릿을 미리 조회해 둔 상태를 재현한다.
            CouponTemplateModel template = couponTemplateRepository.save(new CouponTemplateModel(
                    "동시성 확인 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000),
                    null, ZonedDateTime.now().plusDays(30), 10));
            Long templateId = template.getId();
            CouponTemplateModel staleTemplate = couponTemplateRepository.findById(templateId).orElseThrow();

            // when: 그 사이 원자적 UPDATE로 issuedQuantity가 증가한 뒤, stale 엔티티를 수정해 저장을 시도한다.
            couponTemplateService.reserveQuantity(templateId);
            staleTemplate.update("동시성 확인 쿠폰(수정)", CouponType.FIXED, BigDecimal.valueOf(1000),
                    null, ZonedDateTime.now().plusDays(30), 20);

            // then
            assertThrows(ObjectOptimisticLockingFailureException.class,
                    () -> couponTemplateRepository.save(staleTemplate));
        }
    }

    @DisplayName("쿠폰 발급 요청 시,")
    @Nested
    class RequestIssueConcurrency {

        @DisplayName("동일 사용자가 같은 쿠폰을 동시에 요청해도 발급 요청은 단 한 번만 생성된다.")
        @Test
        void createsIssueRequestOnlyOnce_whenSameUserRequestsConcurrently() throws InterruptedException {
            // given
            UserModel user = userRepository.save(new UserModel(
                    LOGIN_ID, LOGIN_PW, "동시발급테스터", "1990-01-01",
                    "concurcoupon@example.com", Gender.MALE, passwordEncryptor));
            CouponTemplateModel template = couponTemplateRepository.save(new CouponTemplateModel(
                    "동시발급 테스트 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(30), 100));
            Long templateId = template.getId();

            // when
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREAD_COUNT);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);
            List<String> createdRequestIds = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        CouponIssueRequestInfo info = couponFacade.requestIssue(LOGIN_ID, LOGIN_PW, templateId);
                        createdRequestIds.add(info.requestId());
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        if (e.getErrorType() == ErrorType.CONFLICT) {
                            conflictCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            startGate.countDown();
            done.await();
            executor.shutdown();

            // then
            assertAll(
                    () -> assertThat(successCount.get()).isEqualTo(1),
                    () -> assertThat(conflictCount.get()).isEqualTo(THREAD_COUNT - 1),
                    () -> assertThat(createdRequestIds).hasSize(1)
            );
        }
    }
}
