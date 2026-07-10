package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponRepository;
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
    private CouponIssueRequestRepository couponIssueRequestRepository;

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
        return saveTemplate(expiredAt, 100);
    }

    private CouponTemplateModel saveTemplate(ZonedDateTime expiredAt, int totalQuantity) {
        return couponTemplateRepository.save(new CouponTemplateModel(
                "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), BigDecimal.valueOf(10000), expiredAt, totalQuantity));
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
                    () -> assertThat(result.getContent()).hasSize(2)
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

        @DisplayName("발급받은 쿠폰이 있으면 템플릿 정보와 함께 목록이 반환된다.")
        @Test
        void returnsMyIssuedCoupons_withTemplateInfo() {
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
                    () -> assertThat(result).allMatch(info -> info.status() == CouponStatus.AVAILABLE),
                    () -> assertThat(result).anyMatch(info -> info.expiredAt().isAfter(ZonedDateTime.now())),
                    () -> assertThat(result).anyMatch(info -> info.expiredAt().isBefore(ZonedDateTime.now()))
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

        @DisplayName("발급된 쿠폰의 템플릿이 소프트 삭제된 경우 해당 쿠폰은 목록에서 제외된다.")
        @Test
        void excludesIssuedCoupon_whenTemplateIsSoftDeleted() {
            // given
            CouponTemplateModel activeTemplate = saveTemplate(ZonedDateTime.now().plusDays(30));
            CouponTemplateModel deletedTemplate = saveTemplate(ZonedDateTime.now().plusDays(30));
            issuedCouponRepository.save(new IssuedCouponModel(activeTemplate.getId(), savedUser.getId()));
            issuedCouponRepository.save(new IssuedCouponModel(deletedTemplate.getId(), savedUser.getId()));
            couponTemplateService.deleteTemplate(deletedTemplate.getId());

            // when
            List<MyIssuedCouponInfo> result = couponFacade.getMyIssuedCoupons(LOGIN_ID, LOGIN_PW);

            // then
            assertThat(result).hasSize(1);
        }
    }

    @DisplayName("쿠폰 발급을 요청할 때,")
    @Nested
    class RequestIssue {

        @DisplayName("유효한 쿠폰 템플릿으로 발급을 요청하면 PENDING 상태의 발급 요청이 반환되고 DB에 저장된다.")
        @Test
        void issueRequestIsReturnedAndPersisted_whenValid() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));

            // when
            CouponIssueRequestInfo result = couponFacade.requestIssue(LOGIN_ID, LOGIN_PW, template.getId());

            // then
            CouponIssueRequestModel saved = couponIssueRequestRepository.findByRequestId(result.requestId()).orElseThrow();
            assertAll(
                    () -> assertThat(result.couponTemplateId()).isEqualTo(template.getId()),
                    () -> assertThat(result.userId()).isEqualTo(savedUser.getId()),
                    () -> assertThat(result.status()).isEqualTo(CouponIssueRequestStatus.PENDING),
                    () -> assertThat(saved.getCouponTemplateId()).isEqualTo(template.getId())
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿으로 발급을 요청하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTemplateDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.requestIssue(LOGIN_ID, LOGIN_PW, 99999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰 템플릿으로 발급을 요청하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTemplateIsExpired() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().minusDays(1));

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.requestIssue(LOGIN_ID, LOGIN_PW, template.getId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 발급을 요청한 쿠폰을 다시 요청하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenSameUserRequestsSameCouponAgain() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));
            couponFacade.requestIssue(LOGIN_ID, LOGIN_PW, template.getId());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.requestIssue(LOGIN_ID, LOGIN_PW, template.getId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("쿠폰 발급 요청 상태를 조회할 때,")
    @Nested
    class GetIssueRequestStatus {

        @DisplayName("본인의 발급 요청이면 상태가 반환된다.")
        @Test
        void returnsStatus_whenRequesterIsOwner() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));
            CouponIssueRequestInfo requested = couponFacade.requestIssue(LOGIN_ID, LOGIN_PW, template.getId());

            // when
            CouponIssueRequestInfo result = couponFacade.getIssueRequestStatus(LOGIN_ID, LOGIN_PW, requested.requestId());

            // then
            assertAll(
                    () -> assertThat(result.requestId()).isEqualTo(requested.requestId()),
                    () -> assertThat(result.status()).isEqualTo(CouponIssueRequestStatus.PENDING)
            );
        }

        @DisplayName("존재하지 않는 requestId를 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenRequestDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.getIssueRequestStatus(LOGIN_ID, LOGIN_PW, "존재하지-않는-id"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("다른 사용자의 발급 요청을 조회하면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenRequesterIsNotOwner() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));
            CouponIssueRequestInfo requested = couponFacade.requestIssue(LOGIN_ID, LOGIN_PW, template.getId());
            String otherLoginId = "otheruser1";
            userRepository.save(new UserModel(
                    otherLoginId, LOGIN_PW, "다른유저", "1990-01-01", "other@example.com", Gender.MALE, passwordEncryptor));

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.getIssueRequestStatus(otherLoginId, LOGIN_PW, requested.requestId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
