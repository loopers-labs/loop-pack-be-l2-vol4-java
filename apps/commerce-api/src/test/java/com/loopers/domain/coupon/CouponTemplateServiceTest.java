package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.loopers.domain.BaseEntity;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponTemplateServiceTest {

    private CouponTemplateService couponTemplateService;
    private CouponTemplateRepository couponTemplateRepository;

    @BeforeEach
    void setUp() {
        couponTemplateRepository = mock(CouponTemplateRepository.class);
        couponTemplateService = new CouponTemplateService(couponTemplateRepository);
    }

    @DisplayName("쿠폰 템플릿 목록을 페이지 단위로 조회할 때,")
    @Nested
    class FindAll {

        @DisplayName("등록된 쿠폰 템플릿이 존재하면 해당 목록이 페이지 단위로 반환된다.")
        @Test
        void couponTemplatesAreListedByPage_whenTemplatesExist() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인",
                    CouponType.RATE,
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(10000),
                    ZonedDateTime.now().plusDays(30)
            );
            when(couponTemplateRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(template)));

            // when
            Page<CouponTemplateModel> result = couponTemplateService.findAll(pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @DisplayName("쿠폰 템플릿 단건을 조회할 때,")
    @Nested
    class GetTemplate {

        @DisplayName("쿠폰 템플릿이 존재하면 해당 템플릿이 반환된다.")
        @Test
        void couponTemplateIsReturned_whenTemplateExists() {
            // given
            Long couponTemplateId = 1L;
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인",
                    CouponType.RATE,
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(10000),
                    ZonedDateTime.now().plusDays(30)
            );
            when(couponTemplateRepository.findById(couponTemplateId)).thenReturn(Optional.of(template));

            // when
            CouponTemplateModel result = couponTemplateService.getById(couponTemplateId);

            // then
            assertThat(result.getName()).isEqualTo("신규가입 10% 할인");
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿은 조회할 수 없다.")
        @Test
        void couponTemplateCannotBeFound_whenTemplateDoesNotExist() {
            // given
            Long couponTemplateId = 999L;
            when(couponTemplateRepository.findById(couponTemplateId)).thenReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                    () -> couponTemplateService.getById(couponTemplateId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("ID 목록으로 쿠폰 템플릿 Map을 조회할 때,")
    @Nested
    class GetMapByIds {

        @DisplayName("ID 목록에 해당하는 템플릿이 존재하면 ID를 키로 하는 Map이 반환된다.")
        @Test
        void couponTemplateMapIsReturnedByIds_whenIdsExist() throws Exception {
            // given
            CouponTemplateModel template1 = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10),
                    BigDecimal.valueOf(10000), ZonedDateTime.now().plusDays(30));
            CouponTemplateModel template2 = new CouponTemplateModel(
                    "여름 시즌 5000원 할인", CouponType.FIXED, BigDecimal.valueOf(5000),
                    BigDecimal.valueOf(20000), ZonedDateTime.now().plusDays(30));
            setId(template1, 1L);
            setId(template2, 2L);
            Set<Long> ids = Set.of(1L, 2L);
            when(couponTemplateRepository.findAllByIds(ids)).thenReturn(List.of(template1, template2));

            // when
            Map<Long, CouponTemplateModel> result = couponTemplateService.getMapByIds(ids);

            // then
            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> assertThat(result.get(1L)).isEqualTo(template1),
                    () -> assertThat(result.get(2L)).isEqualTo(template2)
            );
        }

        @DisplayName("빈 ID 목록이 주어지면 빈 Map이 반환된다.")
        @Test
        void returnsEmptyMap_whenIdsIsEmpty() {
            // given
            Set<Long> ids = Set.of();
            when(couponTemplateRepository.findAllByIds(ids)).thenReturn(List.of());

            // when
            Map<Long, CouponTemplateModel> result = couponTemplateService.getMapByIds(ids);

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("쿠폰 템플릿을 등록할 때,")
    @Nested
    class CreateTemplate {

        @DisplayName("정상 입력으로 쿠폰 템플릿을 등록하면 입력값이 저장된 템플릿에 반영된다.")
        @Test
        void couponTemplateIsSaved_withGivenValues() {
            // given
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);
            when(couponTemplateRepository.save(any(CouponTemplateModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponTemplateModel result = couponTemplateService.createTemplate(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10),
                    BigDecimal.valueOf(10000), expiredAt);

            // then
            assertAll(
                    () -> assertThat(result.getName()).isEqualTo("신규가입 10% 할인"),
                    () -> assertThat(result.getDiscountPolicy().type()).isEqualTo(CouponType.RATE),
                    () -> assertThat(result.getDiscountPolicy().value()).isEqualByComparingTo(BigDecimal.valueOf(10)),
                    () -> assertThat(result.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(result.getExpiredAt()).isEqualTo(expiredAt)
            );
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때,")
    @Nested
    class UpdateTemplate {

        @DisplayName("존재하는 쿠폰 템플릿을 수정하면 변경된 값이 저장된 템플릿에 반영된다.")
        @Test
        void couponTemplateIsUpdated_withNewValues() {
            // given
            Long couponTemplateId = 1L;
            CouponTemplateModel existing = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10),
                    BigDecimal.valueOf(10000), ZonedDateTime.now().plusDays(30));
            ZonedDateTime newExpiredAt = ZonedDateTime.now().plusDays(60);
            when(couponTemplateRepository.findById(couponTemplateId)).thenReturn(Optional.of(existing));
            when(couponTemplateRepository.save(any(CouponTemplateModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponTemplateModel result = couponTemplateService.updateTemplate(
                    couponTemplateId, "여름 시즌 5000원 할인", CouponType.FIXED,
                    BigDecimal.valueOf(5000), BigDecimal.valueOf(20000), newExpiredAt);

            // then
            assertAll(
                    () -> assertThat(result.getName()).isEqualTo("여름 시즌 5000원 할인"),
                    () -> assertThat(result.getDiscountPolicy().type()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(result.getDiscountPolicy().value()).isEqualByComparingTo(BigDecimal.valueOf(5000)),
                    () -> assertThat(result.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(result.getExpiredAt()).isEqualTo(newExpiredAt)
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿은 수정할 수 없다.")
        @Test
        void couponTemplateCannotBeUpdated_whenTemplateDoesNotExist() {
            // given
            Long couponTemplateId = 999L;
            when(couponTemplateRepository.findById(couponTemplateId)).thenReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                    () -> couponTemplateService.updateTemplate(
                            couponTemplateId, "여름 시즌 5000원 할인", CouponType.FIXED,
                            BigDecimal.valueOf(5000), null, ZonedDateTime.now().plusDays(60)));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿을 삭제할 때,")
    @Nested
    class DeleteTemplate {

        @DisplayName("존재하는 쿠폰 템플릿을 삭제하면 소프트 삭제 처리된다.")
        @Test
        void couponTemplateIsSoftDeleted_whenTemplateExists() {
            // given
            Long couponTemplateId = 1L;
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10),
                    BigDecimal.valueOf(10000), ZonedDateTime.now().plusDays(30));
            when(couponTemplateRepository.findById(couponTemplateId)).thenReturn(Optional.of(template));
            when(couponTemplateRepository.save(template)).thenReturn(template);

            // when
            couponTemplateService.deleteTemplate(couponTemplateId);

            // then
            assertThat(template.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿은 삭제할 수 없다.")
        @Test
        void couponTemplateCannotBeDeleted_whenTemplateDoesNotExist() {
            // given
            Long couponTemplateId = 999L;
            when(couponTemplateRepository.findById(couponTemplateId)).thenReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                    () -> couponTemplateService.deleteTemplate(couponTemplateId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    private void setId(CouponTemplateModel template, Long id) throws Exception {
        Field idField = BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(template, id);
    }
}
