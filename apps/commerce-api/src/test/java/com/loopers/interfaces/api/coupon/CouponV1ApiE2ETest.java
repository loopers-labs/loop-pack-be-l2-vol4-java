package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthHeaders;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String BASE_URL = "/api/v1/coupons";
    private static final String LOGIN_ID = "couponusr1";
    private static final String LOGIN_PW = "Password1!";

    @Autowired
    private TestRestTemplate testRestTemplate;

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

    private HttpEntity<Void> authHeaderEntity(String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, loginId);
        headers.set(AuthHeaders.LOGIN_PW, loginPw);
        return new HttpEntity<>(null, headers);
    }

    private CouponTemplateModel saveTemplate(ZonedDateTime expiredAt) {
        return couponTemplateRepository.save(new CouponTemplateModel(
                "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), BigDecimal.valueOf(10000), expiredAt
        ));
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class Issue {

        @DisplayName("유효한 인증과 유효한 쿠폰 템플릿으로 발급 요청하면 AVAILABLE 상태의 발급 쿠폰이 반환되고 DB에 저장된다.")
        @Test
        void returnsIssuedCouponAndPersists_whenValid() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));

            // when
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssueResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.IssueResponse>> response = testRestTemplate.exchange(
                    BASE_URL + "/" + template.getId() + "/issue",
                    HttpMethod.POST,
                    authHeaderEntity(LOGIN_ID, LOGIN_PW),
                    responseType
            );

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().couponTemplateId()).isEqualTo(template.getId()),
                    () -> assertThat(response.getBody().data().userId()).isEqualTo(savedUser.getId()),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(CouponV1Dto.CouponStatusDto.AVAILABLE),
                    () -> assertThat(issuedCouponRepository.findAllByUserId(savedUser.getId())).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿으로 발급 요청하면 404를 반환한다.")
        @Test
        void returnsNotFound_whenTemplateDoesNotExist() {
            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/99999/issue",
                    HttpMethod.POST,
                    authHeaderEntity(LOGIN_ID, LOGIN_PW),
                    Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰 템플릿으로 발급 요청하면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenTemplateIsExpired() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().minusDays(1));

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/" + template.getId() + "/issue",
                    HttpMethod.POST,
                    authHeaderEntity(LOGIN_ID, LOGIN_PW),
                    Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("LOGIN_ID 헤더가 없으면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenLoginIdHeaderIsMissing() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/" + template.getId() + "/issue",
                    HttpMethod.POST,
                    new HttpEntity<>(null, headers),
                    Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("LOGIN_PW 헤더가 없으면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenLoginPwHeaderIsMissing() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LOGIN_ID, LOGIN_ID);

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/" + template.getId() + "/issue",
                    HttpMethod.POST,
                    new HttpEntity<>(null, headers),
                    Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 틀리면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordIsWrong() {
            // given
            CouponTemplateModel template = saveTemplate(ZonedDateTime.now().plusDays(30));

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/" + template.getId() + "/issue",
                    HttpMethod.POST,
                    authHeaderEntity(LOGIN_ID, "WrongPass1!"),
                    Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
