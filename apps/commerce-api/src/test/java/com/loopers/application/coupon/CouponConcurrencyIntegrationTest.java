package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponRepository;
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

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class CouponConcurrencyIntegrationTest {

    private static final int THREAD_COUNT = 10;
    private static final String LOGIN_ID = "cconcur01";
    private static final String LOGIN_PW = "Password1!";

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰 동시 발급 시,")
    @Nested
    class IssueConcurrency {

        @DisplayName("동일 사용자가 같은 쿠폰을 동시에 요청해도 단 한 번만 발급된다.")
        @Test
        void issuesCouponOnlyOnce_whenSameUserRequestsConcurrently() throws InterruptedException {
            // given
            UserModel user = userRepository.save(new UserModel(
                    LOGIN_ID, LOGIN_PW, "동시발급테스터", "1990-01-01",
                    "concurcoupon@example.com", Gender.MALE, passwordEncryptor));
            CouponTemplateModel template = couponTemplateRepository.save(new CouponTemplateModel(
                    "동시발급 테스트 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(30)));
            Long templateId = template.getId();

            // when
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREAD_COUNT);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);

            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        couponFacade.issue(LOGIN_ID, LOGIN_PW, templateId);
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
            long issuedCount = issuedCouponRepository.findAllByUserId(user.getId()).size();
            assertAll(
                    () -> assertThat(successCount.get()).isEqualTo(1),
                    () -> assertThat(conflictCount.get()).isEqualTo(THREAD_COUNT - 1),
                    () -> assertThat(issuedCount).isEqualTo(1)
            );
        }
    }
}
