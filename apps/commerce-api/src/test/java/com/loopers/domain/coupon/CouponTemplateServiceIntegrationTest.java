package com.loopers.domain.coupon;

import com.loopers.fixture.CouponTemplateFixture;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
@SpringBootTest
public class CouponTemplateServiceIntegrationTest {

    @Autowired
    private CouponTemplateService couponTemplateService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성 시, 저장된 템플릿을 반환한다.")
        @Test
        void returnsSavedTemplate_whenValidInput() {
            // act
            CouponTemplateModel saved = couponTemplateService.create(
                CouponTemplateFixture.NAME, CouponTemplateFixture.TYPE, CouponTemplateFixture.VALUE,
                CouponTemplateFixture.MIN_ORDER_AMOUNT, CouponTemplateFixture.EXPIRED_AT);

            // assert
            assertAll(
                () -> assertThat(saved.getId()).isNotNull(),
                () -> assertThat(saved.getName()).isEqualTo(CouponTemplateFixture.NAME),
                () -> assertThat(saved.getType()).isEqualTo(CouponTemplateFixture.TYPE),
                () -> assertThat(saved.getValue()).isEqualTo(CouponTemplateFixture.VALUE),
                () -> assertThat(saved.getMinOrderAmount()).isEqualTo(CouponTemplateFixture.MIN_ORDER_AMOUNT),
                () -> assertThat(saved.getDeletedAt()).isNull()
            );
        }

        @DisplayName("이미 존재하는 쿠폰명으로 생성 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            // arrange
            couponTemplateService.create(CouponTemplateFixture.NAME, CouponTemplateFixture.TYPE,
                CouponTemplateFixture.VALUE, CouponTemplateFixture.MIN_ORDER_AMOUNT, CouponTemplateFixture.EXPIRED_AT);

            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                couponTemplateService.create(CouponTemplateFixture.NAME, CouponType.FIXED, 3000L, null,
                    CouponTemplateFixture.EXPIRED_AT)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("삭제된 템플릿과 동일한 이름으로 재등록 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenDeletedNameReused() {
            // arrange — 생성 후 소프트딜리트
            CouponTemplateModel template = couponTemplateService.create(CouponTemplateFixture.NAME,
                CouponTemplateFixture.TYPE, CouponTemplateFixture.VALUE,
                CouponTemplateFixture.MIN_ORDER_AMOUNT, CouponTemplateFixture.EXPIRED_AT);
            couponTemplateService.delete(template.getId());

            // act & assert — 삭제돼도 이름 영구 차단 (브랜드 정책 ⑧ 동일)
            CoreException ex = assertThrows(CoreException.class, () ->
                couponTemplateService.create(CouponTemplateFixture.NAME, CouponTemplateFixture.TYPE,
                    CouponTemplateFixture.VALUE, CouponTemplateFixture.MIN_ORDER_AMOUNT, CouponTemplateFixture.EXPIRED_AT)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("쿠폰 템플릿을 단건 조회할 때,")
    @Nested
    class Get {

        @DisplayName("존재하지 않는 ID로 조회 시, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdNotExists() {
            CoreException ex = assertThrows(CoreException.class, () ->
                couponTemplateService.get(UUID.randomUUID())
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 템플릿을 어드민용 get으로 조회 시, 반환된다.")
        @Test
        void returnsTemplate_whenDeletedAndCalledByAdmin() {
            // arrange
            CouponTemplateModel template = couponTemplateService.create(CouponTemplateFixture.NAME,
                CouponTemplateFixture.TYPE, CouponTemplateFixture.VALUE,
                CouponTemplateFixture.MIN_ORDER_AMOUNT, CouponTemplateFixture.EXPIRED_AT);
            couponTemplateService.delete(template.getId());

            // act
            CouponTemplateModel found = couponTemplateService.get(template.getId());

            // assert
            assertThat(found.getId()).isEqualTo(template.getId());
        }

        @DisplayName("삭제된 템플릿을 발급용 getActive로 조회 시, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenDeletedAndCalledByGetActive() {
            // arrange
            CouponTemplateModel template = couponTemplateService.create(CouponTemplateFixture.NAME,
                CouponTemplateFixture.TYPE, CouponTemplateFixture.VALUE,
                CouponTemplateFixture.MIN_ORDER_AMOUNT, CouponTemplateFixture.EXPIRED_AT);
            couponTemplateService.delete(template.getId());

            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                couponTemplateService.getActive(template.getId())
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 값으로 수정 시, 필드가 갱신된다.")
        @Test
        void updatesFields_whenValid() {
            // arrange
            CouponTemplateModel template = couponTemplateService.create(CouponTemplateFixture.NAME,
                CouponTemplateFixture.TYPE, CouponTemplateFixture.VALUE,
                CouponTemplateFixture.MIN_ORDER_AMOUNT, CouponTemplateFixture.EXPIRED_AT);
            ZonedDateTime newExpiredAt = ZonedDateTime.now().plusDays(60);

            // act
            CouponTemplateModel updated = couponTemplateService.update(template.getId(),
                "여름 정액 할인", CouponType.FIXED, 5000L, 20000L, newExpiredAt);

            // assert
            assertAll(
                () -> assertThat(updated.getName()).isEqualTo("여름 정액 할인"),
                () -> assertThat(updated.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(updated.getValue()).isEqualTo(5000L),
                () -> assertThat(updated.getMinOrderAmount()).isEqualTo(20000L),
                () -> assertThat(updated.getExpiredAt()).isEqualTo(newExpiredAt)
            );
        }
    }

    @DisplayName("쿠폰 템플릿을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("삭제 후 deletedAt이 기록되고, 어드민 조회로 보존이 확인된다.")
        @Test
        void softDeletes_whenDeleteCalled() {
            // arrange
            CouponTemplateModel template = couponTemplateService.create(CouponTemplateFixture.NAME,
                CouponTemplateFixture.TYPE, CouponTemplateFixture.VALUE,
                CouponTemplateFixture.MIN_ORDER_AMOUNT, CouponTemplateFixture.EXPIRED_AT);

            // act
            couponTemplateService.delete(template.getId());

            // assert
            CouponTemplateModel deleted = couponTemplateService.get(template.getId());
            assertThat(deleted.getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("쿠폰 템플릿 목록을 조회할 때,")
    @Nested
    class GetList {

        @DisplayName("페이징 조건에 맞게 템플릿 목록을 반환한다.")
        @Test
        void returnsPagedTemplates_whenTemplatesExist() {
            // arrange
            couponTemplateService.create("쿠폰A", CouponType.FIXED, 1000L, null, CouponTemplateFixture.EXPIRED_AT);
            couponTemplateService.create("쿠폰B", CouponType.FIXED, 2000L, null, CouponTemplateFixture.EXPIRED_AT);
            couponTemplateService.create("쿠폰C", CouponType.RATE, 10L, null, CouponTemplateFixture.EXPIRED_AT);

            // act
            Page<CouponTemplateModel> page = couponTemplateService.getList(PageRequest.of(0, 2));

            // assert
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(3),
                () -> assertThat(page.getContent()).hasSize(2),
                () -> assertThat(page.getTotalPages()).isEqualTo(2)
            );
        }
    }
}
