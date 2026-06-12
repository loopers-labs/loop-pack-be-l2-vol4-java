package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserCouponServiceTest {

    private UserCouponService userCouponService;

    @BeforeEach
    void setUp() {
        userCouponService = new UserCouponService(new FakeUserCouponRepository(), new FakeCouponTemplateRepository());
    }

    @DisplayName("유저 쿠폰을 저장할 때,")
    @Nested
    class Save {

        @DisplayName("save() 호출 시 UserCoupon이 저장되어 반환된다.")
        @Test
        void savesUserCoupon() {
            // arrange
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);

            // act
            UserCouponModel saved = userCouponService.save(userCoupon);

            // assert
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getMemberId()).isEqualTo(1L);
            assertThat(saved.getTemplateId()).isEqualTo(10L);
        }
    }

    @DisplayName("유저 쿠폰을 단건 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 ID로 조회하면 UserCoupon을 반환한다.")
        @Test
        void returnsUserCoupon_whenIdExists() {
            // arrange
            UserCouponModel saved = userCouponService.save(new UserCouponModel(1L, 10L));

            // act
            UserCouponModel result = userCouponService.getById(saved.getId());

            // assert
            assertThat(result.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("존재하지 않는 ID로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            assertThatThrownBy(() -> userCouponService.getById(999L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    // ───────────────────────────────────────────────
    // Fake: DB 없이 비즈니스 로직만 격리 검증
    // JOIN이 필요한 조회(memberId, templateId 기반)는 통합 테스트에서 검증
    // ───────────────────────────────────────────────
    private static class FakeUserCouponRepository implements UserCouponRepository {

        private final Map<Long, UserCouponModel> store = new HashMap<>();
        private long sequence = 1L;

        @Override
        public UserCouponModel save(UserCouponModel userCoupon) {
            setId(userCoupon, sequence++);
            store.put(userCoupon.getId(), userCoupon);
            return userCoupon;
        }

        @Override
        public Optional<UserCouponModel> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<UserCouponModel> findAllByMemberId(Long memberId) {
            return store.values().stream().filter(uc -> uc.getMemberId().equals(memberId)).toList();
        }

        @Override
        public Page<UserCouponModel> findAllByTemplateId(Long templateId, PageRequest pageRequest) {
            throw new UnsupportedOperationException("통합 테스트에서 검증");
        }

        private void setId(UserCouponModel model, long id) {
            try {
                var field = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(model, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class FakeCouponTemplateRepository implements CouponTemplateRepository {

        @Override
        public CouponTemplateModel save(CouponTemplateModel template) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CouponTemplateModel> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public org.springframework.data.domain.Page<CouponTemplateModel> findAll(PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }
    }
}
