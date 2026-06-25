package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    private static final String RAW_PASSWORD = "Kyle!2030";
    private static final String CARD_NO = "1234-5678-9012-3456";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser(String loginId) {
        return userJpaRepository.save(UserModel.builder()
            .rawLoginId(loginId)
            .rawPassword(RAW_PASSWORD)
            .rawName("테스트유저")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail(loginId + "@example.com")
            .passwordEncrypter(passwordEncrypter)
            .build());
    }

    private OrderModel saveOrder(Long userId, int finalAmount) {
        return orderJpaRepository.save(OrderModel.builder()
            .userId(userId)
            .orderedAt(ZonedDateTime.now())
            .originalAmount(finalAmount)
            .discountAmount(0)
            .finalAmount(finalAmount)
            .build());
    }

    private HttpEntity<Object> memberJsonRequest(String loginId, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(LOGIN_ID_HEADER, loginId);
        headers.add(LOGIN_PW_HEADER, RAW_PASSWORD);

        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Object> guestJsonRequest(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(body, headers);
    }

    @DisplayName("결제 요청 - POST /api/v1/payments")
    @Nested
    class CreatePayment {

        @DisplayName("정상 요청이면, 201 Created와 함께 PENDING 결제와 거래 식별자가 반환된다.")
        @Test
        void returnsCreated_withPendingPayment() {
            // arrange
            UserModel user = saveUser("kylekim");
            OrderModel order = saveOrder(user.getId(), 78_000);
            given(paymentGateway.requestPayment(any())).willReturn("TX-0001");
            PaymentV1Dto.CreateRequest requestBody = new PaymentV1Dto.CreateRequest(order.getId(), CardType.SAMSUNG, CARD_NO);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, memberJsonRequest("kylekim", requestBody), MAP_RESPONSE);

            // assert
            Map<String, Object> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data).containsOnlyKeys("paymentId", "orderId", "amount", "status", "transactionKey"),
                () -> assertThat(((Number) data.get("orderId")).longValue()).isEqualTo(order.getId()),
                () -> assertThat(((Number) data.get("amount")).intValue()).isEqualTo(78_000),
                () -> assertThat(data.get("status")).isEqualTo("PENDING"),
                () -> assertThat(data.get("transactionKey")).isEqualTo("TX-0001")
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized로 거절된다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            // arrange
            UserModel user = saveUser("kylekim");
            OrderModel order = saveOrder(user.getId(), 78_000);
            PaymentV1Dto.CreateRequest requestBody = new PaymentV1Dto.CreateRequest(order.getId(), CardType.SAMSUNG, CARD_NO);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, guestJsonRequest(requestBody), MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.UNAUTHENTICATED.getCode())
            );
        }

        @DisplayName("대상 주문이 존재하지 않으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenOrderIsAbsent() {
            // arrange
            saveUser("kylekim");
            PaymentV1Dto.CreateRequest requestBody = new PaymentV1Dto.CreateRequest(99999L, CardType.SAMSUNG, CARD_NO);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, memberJsonRequest("kylekim", requestBody), MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("타인 소유의 주문이면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenOrderOwnedByAnotherUser() {
            // arrange
            UserModel owner = saveUser("owner");
            saveUser("requester");
            OrderModel order = saveOrder(owner.getId(), 78_000);
            PaymentV1Dto.CreateRequest requestBody = new PaymentV1Dto.CreateRequest(order.getId(), CardType.SAMSUNG, CARD_NO);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, memberJsonRequest("requester", requestBody), MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("이미 결제가 존재하는 주문이면, 409 Conflict로 거절된다.")
        @Test
        void returnsConflict_whenPaymentAlreadyExists() {
            // arrange
            UserModel user = saveUser("kylekim");
            OrderModel order = saveOrder(user.getId(), 78_000);
            paymentJpaRepository.save(PaymentModel.builder()
                .orderId(order.getId())
                .userId(user.getId())
                .amount(78_000)
                .cardType(CardType.SAMSUNG)
                .rawCardNo(CARD_NO)
                .requestedAt(ZonedDateTime.now())
                .build());
            PaymentV1Dto.CreateRequest requestBody = new PaymentV1Dto.CreateRequest(order.getId(), CardType.SAMSUNG, CARD_NO);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, memberJsonRequest("kylekim", requestBody), MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONFLICT.getCode())
            );
        }

        @DisplayName("카드 번호 형식이 올바르지 않으면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenCardNoFormatIsInvalid() {
            // arrange
            UserModel user = saveUser("kylekim");
            OrderModel order = saveOrder(user.getId(), 78_000);
            PaymentV1Dto.CreateRequest requestBody = new PaymentV1Dto.CreateRequest(order.getId(), CardType.SAMSUNG, "1234-5678");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, memberJsonRequest("kylekim", requestBody), MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }
    }
}
