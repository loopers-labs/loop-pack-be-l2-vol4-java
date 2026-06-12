package com.loopers.application.coupon;

import com.loopers.domain.coupon.DiscountType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponAdminFacadeTest {

    @Autowired private CouponAdminFacade couponAdminFacade;
    @Autowired private CouponFacade couponFacade;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("템플릿을 등록하면 상세 조회로 동일 내용을 읽을 수 있다.")
    @Test
    void create_thenGetTemplate() {
        CouponTemplateInfo created = couponAdminFacade.create(
                new CouponCommand.Create("신규가입 10%", DiscountType.RATE, 10, 1000L, FUTURE));

        CouponTemplateInfo got = couponAdminFacade.getTemplate(created.id());

        assertThat(got.name()).isEqualTo("신규가입 10%");
        assertThat(got.type()).isEqualTo(DiscountType.RATE);
        assertThat(got.value()).isEqualTo(10);
        assertThat(got.minOrderAmount()).isEqualTo(1000);
    }

    @DisplayName("템플릿을 수정하면 변경된 내용이 반영된다.")
    @Test
    void update_changesFields() {
        CouponTemplateInfo created = couponAdminFacade.create(
                new CouponCommand.Create("old", DiscountType.RATE, 10, 0L, FUTURE));

        couponAdminFacade.update(created.id(),
                new CouponCommand.Update("new", DiscountType.FIXED, 5000, 20000L, FUTURE));

        CouponTemplateInfo got = couponAdminFacade.getTemplate(created.id());
        assertThat(got.name()).isEqualTo("new");
        assertThat(got.type()).isEqualTo(DiscountType.FIXED);
        assertThat(got.value()).isEqualTo(5000);
        assertThat(got.minOrderAmount()).isEqualTo(20000);
    }

    @DisplayName("템플릿을 삭제(soft delete)하면 더 이상 조회되지 않는다.")
    @Test
    void delete_thenGetThrowsNotFound() {
        CouponTemplateInfo created = couponAdminFacade.create(
                new CouponCommand.Create("삭제대상", DiscountType.RATE, 10, 0L, FUTURE));

        couponAdminFacade.delete(created.id());

        CoreException result = assertThrows(CoreException.class,
                () -> couponAdminFacade.getTemplate(created.id()));
        assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @DisplayName("발급 내역 조회는 해당 템플릿으로 발급된 쿠폰들을 반환한다.")
    @Test
    void getIssues_returnsIssuedCoupons() {
        CouponTemplateInfo created = couponAdminFacade.create(
                new CouponCommand.Create("발급테스트", DiscountType.RATE, 10, 0L, FUTURE));
        couponFacade.issue(1L, created.id());
        couponFacade.issue(2L, created.id());

        List<CouponInfo> issues = couponAdminFacade.getIssues(created.id(), 0, 20);

        assertThat(issues).hasSize(2);
        assertThat(issues).allMatch(i -> i.couponId().equals(created.id()));
    }
}
