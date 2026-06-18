package com.loopers.domain.coupon;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
public class CouponPersistenceIntegrationTest {

    @Autowired CouponRepository couponRepository;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponModel newCoupon() {
        return new CouponModel("10% 할인", CouponType.RATE, 10L, 5000L, ZonedDateTime.now().plusDays(7));
    }

    @Nested
    @DisplayName("Coupon 템플릿 영속")
    class CouponPersistence {

        @DisplayName("저장하면 id가 부여되고 다시 조회된다")
        @Test
        void given_coupon_when_saveAndFind_then_persisted() {
            CouponModel saved = couponRepository.save(newCoupon());

            CouponModel found = couponRepository.find(saved.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(found.getId()).isEqualTo(saved.getId()),
                    () -> assertThat(found.getType()).isEqualTo(CouponType.RATE),
                    () -> assertThat(found.getValue()).isEqualTo(10L),
                    () -> assertThat(found.getMinOrderAmount()).isEqualTo(5000L),
                    () -> assertThat(found.isActive()).isTrue()
            );
        }

        @DisplayName("soft delete 후 다시 저장하면 비활성 상태가 유지된다 (방식2 동기화)")
        @Test
        void given_savedCoupon_when_deleteAndSave_then_inactivePersisted() {
            CouponModel saved = couponRepository.save(newCoupon());
            saved.delete();
            couponRepository.save(saved);

            CouponModel found = couponRepository.find(saved.getId()).orElseThrow();
            assertThat(found.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("UserCoupon 발급분 영속")
    class UserCouponPersistence {

        @DisplayName("발급분을 저장하면 미사용 상태로 조회된다")
        @Test
        void given_userCoupon_when_save_then_persistedUnused() {
            UserCouponModel saved = userCouponRepository.save(new UserCouponModel(100L, 42L));

            UserCouponModel found = userCouponRepository.find(saved.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(found.getUserId()).isEqualTo(100L),
                    () -> assertThat(found.getCouponId()).isEqualTo(42L),
                    () -> assertThat(found.isUsed()).isFalse()
            );
        }

        @DisplayName("findFirstAvailable은 가장 먼저 발급된 사용 가능한 발급분을 반환한다")
        @Test
        void given_multipleIssued_when_findFirstAvailable_then_oldest() {
            UserCouponModel first = userCouponRepository.save(new UserCouponModel(100L, 42L));
            userCouponRepository.save(new UserCouponModel(100L, 42L));

            UserCouponModel selected = userCouponRepository.findFirstAvailable(100L, 42L).orElseThrow();
            assertThat(selected.getId()).isEqualTo(first.getId());
        }

        @DisplayName("사용 처리(use) 후 저장하면 findFirstAvailable에서 제외된다")
        @Test
        void given_usedCoupon_when_findFirstAvailable_then_empty() {
            UserCouponModel saved = userCouponRepository.save(new UserCouponModel(100L, 42L));
            saved.use();
            userCouponRepository.save(saved);

            assertThat(userCouponRepository.findFirstAvailable(100L, 42L)).isEmpty();
        }

        @DisplayName("타 유저 소유 발급분은 findFirstAvailable에서 조회되지 않는다 (§2 격리)")
        @Test
        void given_otherUserCoupon_when_findFirstAvailable_then_empty() {
            userCouponRepository.save(new UserCouponModel(100L, 42L));

            assertThat(userCouponRepository.findFirstAvailable(999L, 42L)).isEmpty();
        }

        @DisplayName("findByUserId는 사용자의 발급분 목록을 반환한다")
        @Test
        void given_issued_when_findByUserId_then_list() {
            userCouponRepository.save(new UserCouponModel(100L, 42L));
            userCouponRepository.save(new UserCouponModel(100L, 43L));

            assertThat(userCouponRepository.findByUserId(100L, 0, 20)).hasSize(2);
        }
    }
}
