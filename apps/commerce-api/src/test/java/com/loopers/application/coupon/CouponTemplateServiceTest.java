package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTemplateServiceTest {

    private CouponTemplateService couponTemplateService;

    @BeforeEach
    void setUp() {
        couponTemplateService = new CouponTemplateService(new FakeCouponTemplateRepository());
    }

    private CouponTemplateModel fixture() {
        return new CouponTemplateModel("10% 할인", CouponType.RATE, 10L, null, LocalDateTime.now().plusDays(7));
    }

    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 요청으로 생성하면 저장되어 반환된다.")
        @Test
        void savesTemplate_whenRequestIsValid() {
            // arrange
            CouponTemplateModel template = fixture();

            // act
            CouponTemplateModel saved = couponTemplateService.create(template);

            // assert
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getName()).isEqualTo("10% 할인");
        }
    }

    @DisplayName("쿠폰 템플릿을 단건 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 ID로 조회하면 템플릿을 반환한다.")
        @Test
        void returnsTemplate_whenIdExists() {
            // arrange
            CouponTemplateModel saved = couponTemplateService.create(fixture());

            // act
            CouponTemplateModel result = couponTemplateService.getById(saved.getId());

            // assert
            assertThat(result.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("존재하지 않는 ID로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            assertThatThrownBy(() -> couponTemplateService.getById(999L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿 목록을 조회할 때,")
    @Nested
    class GetAll {

        @DisplayName("저장된 템플릿 전체를 반환한다.")
        @Test
        void returnsAllTemplates() {
            // arrange
            couponTemplateService.create(fixture());
            couponTemplateService.create(new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)));

            // act
            Page<CouponTemplateModel> result = couponTemplateService.getAll(PageRequest.of(0, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("name과 isActive를 수정하면 반영된다.")
        @Test
        void updatesFields_whenIdExists() {
            // arrange
            CouponTemplateModel saved = couponTemplateService.create(fixture());

            // act
            CouponTemplateModel updated = couponTemplateService.update(saved.getId(), "20% 할인", false);

            // assert
            assertThat(updated.getName()).isEqualTo("20% 할인");
            assertThat(updated.isActive()).isFalse();
        }

        @DisplayName("존재하지 않는 ID로 수정하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            assertThatThrownBy(() -> couponTemplateService.update(999L, "새 이름", false))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("38: delete() 호출 시 isBlocked=true로 변경된다.")
        @Test
        void blocksTemplate_whenIdExists() {
            // arrange
            CouponTemplateModel saved = couponTemplateService.create(fixture());

            // act
            couponTemplateService.delete(saved.getId());

            // assert
            assertThat(couponTemplateService.getById(saved.getId()).isBlocked()).isTrue();
        }

        @DisplayName("39: 존재하지 않는 ID로 delete() 호출 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            assertThatThrownBy(() -> couponTemplateService.delete(999L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    // ───────────────────────────────────────────────
    // Fake: DB 없이 비즈니스 로직만 격리 검증
    // findAll은 실제 Page 반환 — JOIN 없는 단순 목록이므로 Fake에서 검증 가능
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
            var list = new ArrayList<>(store.values());
            return new PageImpl<>(list, pageRequest, list.size());
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
}
