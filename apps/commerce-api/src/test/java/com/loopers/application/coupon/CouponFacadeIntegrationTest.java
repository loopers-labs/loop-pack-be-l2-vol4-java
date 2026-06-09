package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.application.coupon.MyIssuedCouponInfo;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CouponFacadeIntegrationTest {

    private static final String LOGIN_ID = "couponusr1";
    private static final String LOGIN_PW = "Password1!";

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private CouponTemplateService couponTemplateService;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UserModel savedUser;

    @BeforeEach
    void setUp() {
        savedUser = userRepository.save(new UserModel(
                LOGIN_ID, LOGIN_PW, "쿠폰테스터", "1990-01-01", "coupon@example.com", Gender.MALE, passwordEncryptor
        ));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponTemplateModel saveTemplate(ZonedDateTime expiredAt) {
        return couponTemplateRepository.save(new CouponTemplateModel(
                "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), BigDecimal.valueOf(10000), expiredAt));
    }

    @DisplayName("특정 쿠폰의 발급 내역을 조회할 때,")
    @Nested
    class GetIssuedCouponsByTemplateId {

        @DisplayName("발급된 쿠폰이 있으면 페이지네이션된 발급 내역을 반환한다.")
        @Test
        void returnsPagedIssuedCoupons_whenIssuedCouponsExist() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));
            issuedCouponRepository.save(new IssuedCouponModel(template.getId(), 1L));
            issuedCouponRepository.save(new IssuedCouponModel(template.getId(), 2L));

            // when
            Page<IssuedCouponInfo> result = couponFacade.getIssuedCouponsByTemplateId(
                    template.getId(), PageRequest.of(0, 20));

            // then
            assertAll(
                    () -> assertThat(result.getTotalElements()).isEqualTo(2),
                    () -> assertThat(result.getContent()).hasSize(2),
                    () -> assertThat(result.getContent()).allMatch(info -> info.status() == CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("발급된 쿠폰이 없으면 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoIssuedCouponsExist() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));

            // when
            Page<IssuedCouponInfo> result = couponFacade.getIssuedCouponsByTemplateId(
                    template.getId(), PageRequest.of(0, 20));

            // then
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @DisplayName("템플릿이 만료된 경우 발급 쿠폰의 상태가 EXPIRED로 반환된다.")
        @Test
        void returnsExpiredStatus_whenTemplateIsExpired() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().minusDays(1));
            issuedCouponRepository.save(new IssuedCouponModel(template.getId(), 1L));

            // when
            Page<IssuedCouponInfo> result = couponFacade.getIssuedCouponsByTemplateId(
                    template.getId(), PageRequest.of(0, 20));

            // then
            assertThat(result.getContent().get(0).status()).isEqualTo(CouponStatus.EXPIRED);
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿의 발급 내역을 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTemplateDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.getIssuedCouponsByTemplateId(99999L, PageRequest.of(0, 20)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("내 발급 쿠폰 목록을 조회할 때,")
    @Nested
    class GetMyIssuedCoupons {

        @DisplayName("발급받은 쿠폰이 있으면 해당 목록이 반환되고 만료 여부에 따라 status가 결정된다.")
        @Test
        void returnsMyIssuedCoupons_withResolvedStatus() {
            // given
            CouponTemplateModel activeTemplate = saveTemplate(ZonedDateTime.now().plusDays(30));
            CouponTemplateModel expiredTemplate = saveTemplate(ZonedDateTime.now().minusDays(1));
            issuedCouponRepository.save(new IssuedCouponModel(activeTemplate.getId(), savedUser.getId()));
            issuedCouponRepository.save(new IssuedCouponModel(expiredTemplate.getId(), savedUser.getId()));

            // when
            List<MyIssuedCouponInfo> result = couponFacade.getMyIssuedCoupons(LOGIN_ID, LOGIN_PW);

            // then
            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> assertThat(result).anyMatch(info -> info.status() == CouponStatus.AVAILABLE),
                    () -> assertThat(result).anyMatch(info -> info.status() == CouponStatus.EXPIRED)
            );
        }

        @DisplayName("발급받은 쿠폰이 없으면 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoIssuedCouponsExist() {
            // when
            List<MyIssuedCouponInfo> result = couponFacade.getMyIssuedCoupons(LOGIN_ID, LOGIN_PW);

            // then
            assertThat(result).isEmpty();
        }

        @DisplayName("발급된 쿠폰의 템플릿이 소프트 삭제된 경우 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIssuedCouponTemplateIsDeleted() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));
            issuedCouponRepository.save(new IssuedCouponModel(template.getId(), savedUser.getId()));
            couponTemplateService.deleteTemplate(template.getId());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.getMyIssuedCoupons(LOGIN_ID, LOGIN_PW));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        @DisplayName("유효한 쿠폰 템플릿으로 발급 요청하면 AVAILABLE 상태의 발급 쿠폰이 반환되고 DB에 저장된다.")
        @Test
        void issuedCouponIsReturnedAndPersisted_whenValid() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));

            // when
            IssuedCouponInfo result = couponFacade.issue(LOGIN_ID, LOGIN_PW, template.getId());

            // then
            List<IssuedCouponModel> saved = issuedCouponRepository.findAllByUserId(savedUser.getId());
            assertAll(
                    () -> assertThat(result.couponTemplateId()).isEqualTo(template.getId()),
                    () -> assertThat(result.userId()).isEqualTo(savedUser.getId()),
                    () -> assertThat(result.status()).isEqualTo(CouponStatus.AVAILABLE),
                    () -> assertThat(saved).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿으로 발급 요청하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTemplateDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.issue(LOGIN_ID, LOGIN_PW, 99999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰 템플릿으로 발급 요청하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTemplateIsExpired() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().minusDays(1));

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.issue(LOGIN_ID, LOGIN_PW, template.getId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
