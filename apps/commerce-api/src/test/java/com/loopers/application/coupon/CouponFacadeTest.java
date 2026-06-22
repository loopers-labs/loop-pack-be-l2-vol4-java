package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponFacadeTest {

    private CouponFacade couponFacade;
    private FakeCouponTemplateRepository templateRepo;

    @BeforeEach
    void setUp() {
        templateRepo = new FakeCouponTemplateRepository();
        couponFacade = new CouponFacade(
            new CouponTemplateService(templateRepo),
            new UserCouponService(new FakeUserCouponRepository(), templateRepo)
        );
    }

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        @DisplayName("발급 가능한 템플릿으로 issue()를 호출하면 UserCoupon이 저장되어 반환된다.")
        @Test
        void returnsUserCoupon_whenTemplateCanIssue() {
            // arrange
            CouponTemplateModel template = templateRepo.save(
                new CouponTemplateModel("10% 할인", CouponType.RATE, 10L, null, LocalDateTime.now().plusDays(7)));

            // act
            UserCouponModel result = couponFacade.issue(1L, template.getId());

            // assert
            assertThat(result.getId()).isNotNull();
            assertThat(result.getMemberId()).isEqualTo(1L);
            assertThat(result.getTemplateId()).isEqualTo(template.getId());
        }

        @DisplayName("canIssue()가 false인 템플릿으로 issue()를 호출하면 BAD_REQUEST가 발생한다.")
        @Test
        void throwsBadRequest_whenTemplateCannotIssue() {
            // arrange
            CouponTemplateModel template = templateRepo.save(
                new CouponTemplateModel("10% 할인", CouponType.RATE, 10L, null, LocalDateTime.now().plusDays(7)));
            template.update("10% 할인", false); // isActive=false → canIssue()=false

            // act & assert
            assertThatThrownBy(() -> couponFacade.issue(1L, template.getId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    // ───────────────────────────────────────────────
    // Fakes
    // ───────────────────────────────────────────────
    private static class FakeCouponTemplateRepository implements CouponTemplateRepository {

        private final Map<Long, CouponTemplateModel> store = new HashMap<>();
        private long sequence = 1L;

        @Override
        public CouponTemplateModel save(CouponTemplateModel template) {
            setId(template, sequence++);
            store.put(template.getId(), template);
            return template;
        }

        @Override
        public Optional<CouponTemplateModel> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Page<CouponTemplateModel> findAll(PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        private void setId(CouponTemplateModel model, long id) {
            try {
                var field = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(model, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

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
            throw new UnsupportedOperationException();
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
}
