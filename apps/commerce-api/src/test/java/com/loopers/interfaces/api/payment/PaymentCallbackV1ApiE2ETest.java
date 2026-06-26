package com.loopers.interfaces.api.payment;

import com.loopers.domain.order.OrderItemData;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentCallbackV1ApiE2ETest {

    private static final String CALLBACK_URL = "/api/v1/payments/callback";
    private static final String CARD_NO = "1234-5678-9814-1451";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PasswordEncryptor passwordEncryptor;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private PaymentModel setupPendingPaymentWithKey(String key) {
        UserModel user = userRepository.save(new UserModel("user01", "Password1!", "홍길동", "1990-01-01",
                "user@example.com", Gender.MALE, passwordEncryptor));
        OrderModel order = orderRepository.save(OrderModel.create(user.getId(),
                List.of(new OrderItemData(1L, "상품", BigDecimal.valueOf(5000), 1L)), BigDecimal.ZERO));
        PaymentModel payment = PaymentModel.pending(
                order.getId(), order.getOrderNumber(), user.getId(), CardType.SAMSUNG, CARD_NO, 5000L);
        payment.attachTransactionKey(key);
        return paymentRepository.save(payment);
    }

    private HttpEntity<PaymentV1Dto.CallbackRequest> noAuthJson(PaymentV1Dto.CallbackRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @DisplayName("로그인 헤더 없이 SUCCESS 콜백을 보내면 200 OK 이고 결제가 PAID, 주문이 PAID 로 확정된다.")
    @Test
    void confirmsPaid_onSuccessCallback_withoutAuth() {
        // given
        PaymentModel payment = setupPendingPaymentWithKey("20260626:TR:abc");
        PaymentV1Dto.CallbackRequest body = new PaymentV1Dto.CallbackRequest(
                "20260626:TR:abc", payment.getOrderNumber(), "SAMSUNG", CARD_NO, 5000L, "SUCCESS", "정상 승인");

        // when
        ParameterizedTypeReference<ApiResponse<Object>> type = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(CALLBACK_URL, HttpMethod.POST, noAuthJson(body), type);

        // then
        PaymentModel reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        OrderModel order = orderRepository.findByOrderNumber(payment.getOrderNumber()).orElseThrow();
        assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PAID),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
        );
    }
}
